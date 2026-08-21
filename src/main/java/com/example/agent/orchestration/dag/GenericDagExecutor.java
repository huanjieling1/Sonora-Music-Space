package com.example.agent.orchestration.dag;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.evaluation.EvaluationDecision;
import com.example.agent.agent.evaluation.TaskEvaluation;
import com.example.agent.agent.evaluation.TaskEvaluator;
import com.example.agent.agent.planner.ReferenceResolution;
import com.example.agent.agent.planner.ReferenceResolutionContext;
import com.example.agent.agent.planner.TaskResultStore;
import com.example.agent.agent.planner.ValueExpressionResolver;
import com.example.agent.orchestration.confirmation.ConfirmationManager;
import com.example.agent.orchestration.confirmation.ConfirmationRequest;
import com.example.agent.orchestration.observability.PlannerObservability;
import com.example.agent.orchestration.replanning.BoundedReplanner;
import com.example.agent.orchestration.replanning.ReplanRecord;
import com.example.agent.orchestration.replanning.ReplanRequest;
import com.example.agent.orchestration.replanning.ReplanResult;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One runtime for every validated CompiledPlan; no route-specific handler is required. */
@Component
public final class GenericDagExecutor {
    private final AgentCapabilityRegistry registry;
    private final ValueExpressionResolver resolver;
    private final DagExecutionPersistence persistence;
    private final TaskEvaluator taskEvaluator;
    private final BoundedReplanner boundedReplanner;
    private final ConfirmationManager confirmationManager;
    private final PlannerObservability observability;
    private final ExecutorService taskPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "generic-dag-task");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentHashMap<UUID, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    @Autowired
    public GenericDagExecutor(AgentCapabilityRegistry registry,
                              ValueExpressionResolver resolver,
                              DagExecutionPersistence persistence,
                              TaskEvaluator taskEvaluator,
                              BoundedReplanner boundedReplanner,
                              ConfirmationManager confirmationManager,
                              PlannerObservability observability) {
        this.registry = registry;
        this.resolver = resolver;
        this.persistence = persistence;
        this.taskEvaluator = taskEvaluator;
        this.boundedReplanner = boundedReplanner;
        this.confirmationManager = confirmationManager;
        this.observability = observability;
    }

    public GenericDagExecutor(AgentCapabilityRegistry registry,
                              ValueExpressionResolver resolver,
                              DagExecutionPersistence persistence,
                              TaskEvaluator taskEvaluator,
                              BoundedReplanner boundedReplanner,
                              ConfirmationManager confirmationManager) {
        this(registry, resolver, persistence, taskEvaluator, boundedReplanner,
                confirmationManager, PlannerObservability.noop());
    }

    public GenericDagExecutor(AgentCapabilityRegistry registry,
                              ValueExpressionResolver resolver,
                              DagExecutionPersistence persistence,
                              TaskEvaluator taskEvaluator,
                              BoundedReplanner boundedReplanner) {
        this(registry, resolver, persistence, taskEvaluator, boundedReplanner,
                new ConfirmationManager());
    }

    public GenericDagExecutor(AgentCapabilityRegistry registry,
                              ValueExpressionResolver resolver,
                              DagExecutionPersistence persistence,
                              TaskEvaluator taskEvaluator) {
        this(registry, resolver, persistence, taskEvaluator,
                new BoundedReplanner(registry,
                        new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()));
    }

    /** Backward-compatible constructor for tests and embedded runtimes. */
    public GenericDagExecutor(AgentCapabilityRegistry registry,
                              ValueExpressionResolver resolver,
                              DagExecutionPersistence persistence) {
        this(registry, resolver, persistence, new TaskEvaluator(new com.example.agent.agent.planner.SafeJsonPath(
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules())));
    }

    public DagExecutionSnapshot execute(DagExecutionCommand command, GenericDagTaskExecutor executor) {
        if (command == null || executor == null) throw new IllegalArgumentException("DAG 执行参数不能为空");
        if (persistence.load(command.workflowId(), command.principalId()).isPresent()) {
            throw new IllegalStateException("工作流已经存在，请使用 resume：" + command.workflowId());
        }
        MutableExecution execution = MutableExecution.create(command);
        return run(execution, command.profileRoot(), command.allowedSensitiveProfilePaths(),
                command.options(), executor);
    }

    public DagExecutionSnapshot resume(UUID workflowId, String principalId,
                                       Map<String, Object> userReplies, Object profileRoot,
                                       Set<String> allowedSensitiveProfilePaths,
                                       DagExecutionOptions options,
                                       GenericDagTaskExecutor executor) {
        DagExecutionSnapshot persisted = persistence.load(workflowId, principalId)
                .orElseThrow(() -> new IllegalArgumentException("找不到当前用户的工作流：" + workflowId));
        if (persisted.status() != DagWorkflowStatus.WAITING_USER) {
            throw new IllegalStateException("只有 WAITING_USER 工作流可以恢复");
        }
        MutableExecution execution = MutableExecution.restore(persisted);
        execution.userInputs.putAll(userReplies == null ? Map.of() : userReplies);
        for (MutableTask task : execution.tasks.values()) {
            if (task.status == DagTaskStatus.WAITING_USER) resumeWaitingTask(execution, task);
        }
        execution.status = DagWorkflowStatus.RUNNING;
        return run(execution, profileRoot,
                allowedSensitiveProfilePaths == null ? Set.of() : allowedSensitiveProfilePaths,
                options == null ? DagExecutionOptions.defaults() : options, executor);
    }

    public Optional<DagExecutionSnapshot> snapshot(UUID workflowId, String principalId) {
        return persistence.load(workflowId, principalId);
    }

    private void resumeWaitingTask(MutableExecution execution, MutableTask task) {
        if (task.confirmationRequest != null
                && task.waitingSlot.equals(task.confirmationRequest.replySlot())) {
            task.confirmationRequest = confirmationManager.expireIfNeeded(task.confirmationRequest);
            if (task.confirmationRequest.status() == ConfirmationRequest.Status.EXPIRED) {
                task.status = DagTaskStatus.FAILED;
                task.errorCode = "CONFIRMATION_EXPIRED";
                task.message = "确认请求已经过期，禁止执行原副作用";
                task.waitingSlot = "";
                return;
            }
            if (!execution.userInputs.containsKey(task.waitingSlot)) return;
            try {
                task.confirmationRequest = confirmationManager.respond(task.confirmationRequest,
                        execution.principalId, execution.userInputs.get(task.waitingSlot));
            } catch (IllegalArgumentException invalidReply) {
                task.message = invalidReply.getMessage();
                return;
            }
            if (task.confirmationRequest.status() == ConfirmationRequest.Status.APPROVED) {
                task.status = DagTaskStatus.PENDING;
                task.errorCode = "";
                task.message = "";
                task.waitingSlot = "";
            } else if (task.confirmationRequest.status() == ConfirmationRequest.Status.REJECTED) {
                task.status = DagTaskStatus.SKIPPED;
                task.errorCode = "CONFIRMATION_REJECTED";
                task.message = "用户拒绝执行该操作，已跳过对应分支";
                task.waitingSlot = "";
            }
            return;
        }
        if (execution.userInputs.containsKey(task.waitingSlot)) {
            task.status = task.replanPending ? DagTaskStatus.FAILED : DagTaskStatus.PENDING;
            task.replanPending = false;
            task.message = "";
            task.waitingSlot = "";
        }
    }

    public boolean cancel(UUID workflowId, String principalId) {
        ActiveRun active = activeRuns.get(workflowId);
        if (active != null) {
            if (!active.principalId().equals(principalId)) return false;
            active.cancel().set(true);
            return true;
        }
        Optional<DagExecutionSnapshot> persisted = persistence.load(workflowId, principalId);
        if (persisted.isEmpty() || terminal(persisted.get().status())) return false;
        MutableExecution execution = MutableExecution.restore(persisted.get());
        execution.cancelRemaining();
        execution.status = DagWorkflowStatus.CANCELLED;
        persistence.save(execution.snapshot());
        return true;
    }

    private DagExecutionSnapshot run(MutableExecution execution, Object profileRoot,
                                     Set<String> allowedSensitiveProfilePaths,
                                     DagExecutionOptions options,
                                     GenericDagTaskExecutor executor) {
        AtomicBoolean cancel = new AtomicBoolean(false);
        ActiveRun activeRun = new ActiveRun(execution.principalId, cancel);
        if (activeRuns.putIfAbsent(execution.workflowId, activeRun) != null) {
            throw new IllegalStateException("工作流正在执行：" + execution.workflowId);
        }
        long workflowDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(options.workflowTimeoutSeconds());
        TaskResultStore resultStore = resultStore(execution);
        try {
            persist(execution);
            while (true) {
                if (cancel.get()) {
                    execution.cancelRemaining();
                    execution.status = DagWorkflowStatus.CANCELLED;
                    return persist(execution);
                }
                if (System.nanoTime() >= workflowDeadline) {
                    execution.failRemaining("WORKFLOW_TIMEOUT", "工作流超过总时间限制");
                    execution.status = DagWorkflowStatus.FAILED;
                    return persist(execution);
                }
                if (executor instanceof ReplanningDagTaskExecutor replanningExecutor) {
                    Optional<MutableTask> candidate = execution.replanCandidate();
                    if (candidate.isPresent()) {
                        ReplanResult result = replan(execution, candidate.get(), replanningExecutor);
                        if (result.kind() == ReplanResult.Kind.APPLIED) {
                            resultStore = resultStore(execution);
                            persist(execution);
                            continue;
                        }
                        if (result.kind() == ReplanResult.Kind.ASK_USER) {
                            execution.status = DagWorkflowStatus.WAITING_USER;
                            return persist(execution);
                        }
                    }
                }
                execution.skipBlockedDownstream();
                if (execution.tasks.values().stream().anyMatch(task -> task.status == DagTaskStatus.WAITING_USER)) {
                    execution.status = DagWorkflowStatus.WAITING_USER;
                    return persist(execution);
                }
                evaluateReady(execution, profileRoot, allowedSensitiveProfilePaths, resultStore);
                List<MutableTask> ready = execution.readyTasks();
                if (ready.isEmpty()) {
                    if (execution.allTerminal()) {
                        boolean failed = execution.tasks.values().stream()
                                .anyMatch(task -> task.status == DagTaskStatus.FAILED);
                        execution.status = failed || !execution.allGoalsAccepted()
                                ? DagWorkflowStatus.FAILED : DagWorkflowStatus.COMPLETED;
                        return persist(execution);
                    }
                    observability.deadlock(execution.workflowId, execution.plan.planId().toString());
                    execution.failRemaining("DAG_DEADLOCK", "没有可执行任务且工作流未结束");
                    execution.status = DagWorkflowStatus.FAILED;
                    return persist(execution);
                }
                List<MutableTask> batch = selectBatch(ready, options.maxConcurrency());
                executeBatch(execution, batch, profileRoot, allowedSensitiveProfilePaths,
                        resultStore, workflowDeadline, cancel, options.taskTimeoutCapSeconds(), executor);
                persist(execution);
            }
        } finally {
            activeRuns.remove(execution.workflowId, activeRun);
        }
    }

    private ReplanResult replan(MutableExecution execution, MutableTask failed,
                                ReplanningDagTaskExecutor executor) {
        int attempt = execution.replanRecords.size() + 1;
        Set<String> previous = execution.replanRecords.stream().map(ReplanRecord::planFingerprint)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> approvals = execution.userInputs.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("replan.replay.") && Boolean.TRUE.equals(entry.getValue()))
                .map(entry -> entry.getKey().substring("replan.replay.".length()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        LinkedHashMap<String, DagTaskState> states = new LinkedHashMap<>();
        execution.tasks.forEach((id, state) -> states.put(id, state.snapshot()));
        ReplanRequest request = boundedReplanner.prepare(execution.workflowId, execution.plan,
                failed.taskId, failed.errorCode, failed.message, attempt, states,
                execution.userInputs, previous, approvals);
        execution.status = DagWorkflowStatus.REPLANNING;
        ReplanResult result = boundedReplanner.replan(request, executor::replan);
        ReplanRecord record = boundedReplanner.record(request, result);
        execution.replanRecords.add(record);
        observability.replan(execution.workflowId, record);
        switch (result.kind()) {
            case APPLIED -> {
                execution.applyReplan(result.updatedPlan(), result.replacedTaskIds());
                execution.status = DagWorkflowStatus.RUNNING;
            }
            case ASK_USER -> {
                failed.status = DagTaskStatus.WAITING_USER;
                failed.waitingSlot = result.waitingSlot();
                failed.message = result.message();
                failed.replanPending = true;
            }
            case FAIL -> {
                failed.status = DagTaskStatus.FAILED;
                failed.errorCode = "REPLAN_TERMINAL";
                failed.message = "REPLAN_TERMINAL: " + result.message();
                execution.status = DagWorkflowStatus.RUNNING;
            }
        }
        return result;
    }

    private TaskResultStore resultStore(MutableExecution execution) {
        TaskResultStore resultStore = new TaskResultStore(execution.plan, registry);
        execution.tasks.values().stream().filter(task -> task.status == DagTaskStatus.COMPLETED)
                .map(task -> task.result).filter(java.util.Objects::nonNull).forEach(resultStore::store);
        return resultStore;
    }

    private void evaluateReady(MutableExecution execution, Object profileRoot,
                               Set<String> allowedSensitiveProfilePaths,
                               TaskResultStore resultStore) {
        for (MutableTask state : execution.tasks.values()) {
            if (state.status != DagTaskStatus.PENDING && state.status != DagTaskStatus.RETRYING) continue;
            PlanTask task = execution.taskSpecs.get(state.taskId);
            if (!task.dependencies().stream().allMatch(dependency ->
                    execution.tasks.get(dependency).status == DagTaskStatus.COMPLETED)) continue;
            boolean enabled = true;
            for (ValueExpression condition : task.activationConditions()) {
                ReferenceResolution resolution = resolver.resolve(condition,
                        resolutionContext(execution, task.id(), profileRoot,
                                allowedSensitiveProfilePaths, resultStore));
                if (!resolution.resolved()) {
                    state.status = DagTaskStatus.FAILED;
                    state.errorCode = resolution.error().code();
                    state.message = resolution.error().code() + ": " + resolution.error().message();
                    enabled = false;
                    break;
                }
                if (!truthy(resolution.value())) enabled = false;
            }
            if (state.status == DagTaskStatus.FAILED) continue;
            if (!enabled) {
                state.status = DagTaskStatus.SKIPPED;
                state.message = "条件分支未启用";
            } else {
                state.status = DagTaskStatus.READY;
            }
        }
    }

    private List<MutableTask> selectBatch(List<MutableTask> ready, int maxConcurrency) {
        List<MutableTask> readOnly = ready.stream().filter(task -> capability(task).sideEffect()
                        == CapabilitySideEffect.READ_ONLY)
                .limit(maxConcurrency).toList();
        return readOnly.isEmpty() ? List.of(ready.get(0)) : readOnly;
    }

    private void executeBatch(MutableExecution execution, List<MutableTask> batch,
                              Object profileRoot, Set<String> allowedSensitiveProfilePaths,
                              TaskResultStore resultStore, long workflowDeadline,
                              AtomicBoolean cancel, int taskTimeoutCapSeconds,
                              GenericDagTaskExecutor executor) {
        LinkedHashMap<MutableTask, Future<DagTaskOutcome>> futures = new LinkedHashMap<>();
        LinkedHashMap<MutableTask, Map<String, Object>> evaluationInputs = new LinkedHashMap<>();
        LinkedHashMap<MutableTask, Long> startedAt = new LinkedHashMap<>();
        for (MutableTask state : batch) {
            PlanTask task = execution.taskSpecs.get(state.taskId);
            LinkedHashMap<String, Object> resolvedInputs = new LinkedHashMap<>();
            ReferenceResolution unresolved = null;
            for (Map.Entry<String, ValueExpression> input : task.inputs().entrySet()) {
                ReferenceResolution resolution = resolver.resolve(input.getValue(),
                        resolutionContext(execution, task.id(), profileRoot,
                                allowedSensitiveProfilePaths, resultStore));
                if (!resolution.resolved()) {
                    unresolved = resolution;
                    break;
                }
                resolvedInputs.put(input.getKey(), resolution.value());
            }
            if (unresolved != null) {
                if ("USER_INPUT_NOT_FOUND".equals(unresolved.error().code())) {
                    state.status = DagTaskStatus.WAITING_USER;
                    state.waitingSlot = unresolved.error().reference();
                    state.message = "请补充：" + state.waitingSlot;
                } else {
                    state.status = DagTaskStatus.FAILED;
                    state.errorCode = unresolved.error().code();
                    state.message = unresolved.error().code() + ": " + unresolved.error().message();
                }
                continue;
            }
            AgentCapabilityDefinition capability = capability(state);
            if (capability.sideEffect() != CapabilitySideEffect.READ_ONLY && state.idempotencyKey.isBlank()) {
                state.idempotencyKey = idempotencyKey(execution.workflowId, state.taskId);
            }
            if (!confirmBeforeExecution(execution, state, task, capability, resolvedInputs)) continue;
            state.status = DagTaskStatus.RUNNING;
            state.attempts++;
            DagTaskExecutionRequest request = new DagTaskExecutionRequest(execution.workflowId,
                    execution.principalId, task, resolvedInputs, state.attempts, state.idempotencyKey);
            observability.taskStarted(execution.workflowId, task, capability.sideEffect(),
                    state.attempts, state.idempotencyKey);
            startedAt.put(state, System.nanoTime());
            futures.put(state, taskPool.submit(() -> executor.execute(request)));
            evaluationInputs.put(state, Map.copyOf(resolvedInputs));
        }
        persist(execution);
        for (Map.Entry<MutableTask, Future<DagTaskOutcome>> entry : futures.entrySet()) {
            MutableTask state = entry.getKey();
            AgentCapabilityDefinition capability = capability(state);
            DagTaskOutcome outcome = await(entry.getValue(),
                    Math.min(capability.executionPolicy().timeoutSeconds(), taskTimeoutCapSeconds),
                    workflowDeadline, cancel);
            if (cancel.get()) {
                entry.getValue().cancel(true);
                state.status = DagTaskStatus.CANCELLED;
                state.message = "任务已取消";
                recordTaskFinished(execution, state, capability, startedAt.get(state));
                continue;
            }
            applyOutcome(state, outcome, capability, evaluationInputs.getOrDefault(state, Map.of()), resultStore);
            recordTaskFinished(execution, state, capability, startedAt.get(state));
        }
    }

    private void recordTaskFinished(MutableExecution execution, MutableTask state,
                                    AgentCapabilityDefinition capability, Long startedAt) {
        long duration = startedAt == null ? 0
                : TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedAt));
        observability.taskFinished(execution.workflowId, execution.taskSpecs.get(state.taskId),
                capability.sideEffect(), state.idempotencyKey, duration, state.status.name(),
                state.errorCode, state.evaluation);
    }

    private boolean confirmBeforeExecution(MutableExecution execution, MutableTask state, PlanTask task,
                                           AgentCapabilityDefinition capability,
                                           Map<String, Object> resolvedInputs) {
        if (!confirmationManager.required(capability)) return true;
        Map<String, Object> inputs = Map.copyOf(resolvedInputs);
        if (state.confirmationRequest == null
                || !state.confirmationRequest.pendingInputs().equals(inputs)
                || !state.confirmationRequest.idempotencyKey().equals(state.idempotencyKey)) {
            state.confirmationRequest = confirmationManager.create(execution.workflowId,
                    execution.principalId, task.id(), capability, inputs, state.idempotencyKey);
            Object preauthorization = execution.userInputs.get("confirmation." + task.id());
            if (preauthorization != null) {
                try {
                    state.confirmationRequest = confirmationManager.respond(state.confirmationRequest,
                            execution.principalId, preauthorization);
                } catch (IllegalArgumentException ignored) {
                    // Invalid preauthorization is treated as absent and shown as a normal confirmation request.
                }
            }
        }
        state.confirmationRequest = confirmationManager.expireIfNeeded(state.confirmationRequest);
        if (state.confirmationRequest.status() == ConfirmationRequest.Status.REJECTED) {
            state.status = DagTaskStatus.SKIPPED;
            state.errorCode = "CONFIRMATION_REJECTED";
            state.message = "用户拒绝执行该操作，已跳过对应分支";
            return false;
        }
        if (state.confirmationRequest.status() == ConfirmationRequest.Status.EXPIRED) {
            state.status = DagTaskStatus.FAILED;
            state.errorCode = "CONFIRMATION_EXPIRED";
            state.message = "确认请求已经过期，禁止执行原副作用";
            return false;
        }
        if (!confirmationManager.authorized(state.confirmationRequest, inputs, state.idempotencyKey)) {
            state.status = DagTaskStatus.WAITING_USER;
            state.errorCode = "CONFIRMATION_REQUIRED";
            state.waitingSlot = state.confirmationRequest.replySlot();
            state.message = state.confirmationRequest.prompt();
            return false;
        }
        return true;
    }

    private void applyOutcome(MutableTask state, DagTaskOutcome outcome,
                              AgentCapabilityDefinition capability, Map<String, Object> resolvedInputs,
                              TaskResultStore resultStore) {
        if (outcome == null) outcome = DagTaskOutcome.failure("NULL_TASK_OUTCOME", "任务没有返回结果", true);
        if (outcome.kind() == DagTaskOutcome.Kind.WAITING_USER) {
            // Asking for data is a pause before side-effect execution, not a consumed retry attempt.
            state.attempts = Math.max(0, state.attempts - 1);
            state.status = DagTaskStatus.WAITING_USER;
            state.errorCode = "WAITING_USER";
            state.waitingSlot = outcome.waitingSlot();
            state.message = outcome.message();
            return;
        }
        if (outcome.kind() == DagTaskOutcome.Kind.SUCCESS) {
            PlanTask task = state.owner.taskSpecs.get(state.taskId);
            TaskEvaluation evaluation = taskEvaluator.evaluate(task, capability, resolvedInputs, outcome.result());
            state.evaluation = evaluation;
            if (evaluation.decision() != EvaluationDecision.PASS) {
                applyEvaluationDecision(state, capability, evaluation);
                return;
            }
            try {
                resultStore.store(outcome.result());
                state.result = outcome.result();
                state.status = DagTaskStatus.COMPLETED;
                state.errorCode = "";
                state.message = "";
            } catch (RuntimeException invalid) {
                failOrRetry(state, capability, "INVALID_TYPED_RESULT", invalid.getMessage(), false);
            }
            return;
        }
        failOrRetry(state, capability, outcome.errorCode(), outcome.message(), outcome.retryable());
    }

    private static void applyEvaluationDecision(MutableTask state, AgentCapabilityDefinition capability,
                                                TaskEvaluation evaluation) {
        switch (evaluation.decision()) {
            case PASS -> throw new IllegalStateException("PASS 应在写入结果路径处理");
            case REVISE -> failOrRetry(state, capability, "TASK_EVALUATION_REVISE",
                    evaluation.correction(), true);
            case REPLAN -> {
                state.status = DagTaskStatus.FAILED;
                state.errorCode = "TASK_EVALUATION_REPLAN";
                state.message = "TASK_EVALUATION_REPLAN: " + evaluation.correction();
            }
            case ASK_USER -> {
                state.attempts = Math.max(0, state.attempts - 1);
                state.status = DagTaskStatus.WAITING_USER;
                state.waitingSlot = evaluation.waitingSlot();
                state.message = evaluation.correction();
            }
            case FAIL -> {
                state.status = DagTaskStatus.FAILED;
                state.errorCode = "TASK_EVALUATION_FAILED";
                state.message = "TASK_EVALUATION_FAILED: " + evaluation.correction();
            }
        }
    }

    private static void failOrRetry(MutableTask state, AgentCapabilityDefinition capability,
                                    String code, String message, boolean retryable) {
        PlanTask task = state.owner.taskSpecs.get(state.taskId);
        boolean canRetry = retryable && capability.executionPolicy().idempotent()
                && state.attempts < task.maxAttempts();
        state.status = canRetry ? DagTaskStatus.RETRYING : DagTaskStatus.FAILED;
        state.errorCode = code == null || code.isBlank() ? "TASK_FAILED" : code;
        state.message = state.errorCode
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private DagTaskOutcome await(Future<DagTaskOutcome> future, int timeoutSeconds,
                                 long workflowDeadline, AtomicBoolean cancel) {
        long taskDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (true) {
            if (cancel.get()) {
                future.cancel(true);
                return DagTaskOutcome.failure("TASK_CANCELLED", "任务已取消", false);
            }
            long remaining = Math.min(taskDeadline, workflowDeadline) - System.nanoTime();
            if (remaining <= 0) {
                future.cancel(true);
                return DagTaskOutcome.failure("TASK_TIMEOUT", "任务执行超时", true);
            }
            try {
                return future.get(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining), 200),
                        TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // Poll again so cancellation remains responsive.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                return DagTaskOutcome.failure("SCHEDULER_INTERRUPTED", "调度器被中断", false);
            } catch (ExecutionException failed) {
                Throwable cause = failed.getCause();
                return DagTaskOutcome.failure("TASK_EXECUTION_EXCEPTION",
                        cause == null || cause.getMessage() == null ? "任务执行异常" : cause.getMessage(), true);
            }
        }
    }

    private ReferenceResolutionContext resolutionContext(MutableExecution execution, String taskId,
                                                         Object profileRoot,
                                                         Set<String> allowedSensitiveProfilePaths,
                                                         TaskResultStore resultStore) {
        return new ReferenceResolutionContext(taskId, execution.principalId, execution.principalId,
                profileRoot, execution.userInputs, allowedSensitiveProfilePaths, resultStore);
    }

    private AgentCapabilityDefinition capability(MutableTask task) {
        PlanTask spec = task.owner.taskSpecs.get(task.taskId);
        return registry.find(spec.capabilityId()).orElseThrow();
    }

    private DagExecutionSnapshot persist(MutableExecution execution) {
        execution.updatedAt = Instant.now();
        DagExecutionSnapshot snapshot = execution.snapshot();
        persistence.save(snapshot);
        return snapshot;
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof java.util.Collection<?> collection) return !collection.isEmpty();
        return true;
    }

    private static String idempotencyKey(UUID workflowId, String taskId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((workflowId + ":" + taskId).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean terminal(DagWorkflowStatus status) {
        return status == DagWorkflowStatus.COMPLETED || status == DagWorkflowStatus.FAILED
                || status == DagWorkflowStatus.CANCELLED;
    }

    @PreDestroy
    void shutdown() {
        taskPool.shutdownNow();
    }

    private static final class MutableExecution {
        private final UUID workflowId;
        private final String principalId;
        private final String conversationId;
        private com.example.agent.agent.contract.planning.CompiledPlan plan;
        private final LinkedHashMap<String, PlanTask> taskSpecs = new LinkedHashMap<>();
        private final LinkedHashMap<String, MutableTask> tasks = new LinkedHashMap<>();
        private final LinkedHashMap<String, Object> userInputs = new LinkedHashMap<>();
        private final ArrayList<ReplanRecord> replanRecords = new ArrayList<>();
        private DagWorkflowStatus status;
        private final Instant createdAt;
        private Instant updatedAt;

        private MutableExecution(UUID workflowId, String principalId, String conversationId,
                                 com.example.agent.agent.contract.planning.CompiledPlan plan,
                                 DagWorkflowStatus status, Map<String, Object> userInputs,
                                 Instant createdAt, Instant updatedAt,
                                 List<ReplanRecord> replanRecords) {
            this.workflowId = workflowId;
            this.principalId = principalId;
            this.conversationId = conversationId;
            this.plan = plan;
            this.status = status;
            this.userInputs.putAll(userInputs);
            this.replanRecords.addAll(replanRecords == null ? List.of() : replanRecords);
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            plan.tasks().forEach(task -> taskSpecs.put(task.id(), task));
        }

        static MutableExecution create(DagExecutionCommand command) {
            Instant now = Instant.now();
            MutableExecution value = new MutableExecution(command.workflowId(), command.principalId(),
                    command.conversationId(), command.plan(), DagWorkflowStatus.RUNNING,
                    command.userInputs(), now, now, List.of());
            command.plan().tasks().forEach(task -> value.tasks.put(task.id(), new MutableTask(value,
                    task.id(), DagTaskStatus.PENDING, 0, "", "", "", null, null,
                    "", false, null)));
            return value;
        }

        static MutableExecution restore(DagExecutionSnapshot snapshot) {
            MutableExecution value = new MutableExecution(snapshot.workflowId(), snapshot.principalId(),
                    snapshot.conversationId(), snapshot.plan(), snapshot.status(), snapshot.userInputs(),
                    snapshot.createdAt(), snapshot.updatedAt(), snapshot.replanRecords());
            snapshot.tasks().forEach(task -> value.tasks.put(task.taskId(), new MutableTask(value,
                    task.taskId(), task.status(), task.attempts(), task.message(), task.waitingSlot(),
                    task.idempotencyKey(), task.result(), task.evaluation(), task.errorCode(),
                    task.replanPending(), task.confirmationRequest())));
            return value;
        }

        Optional<MutableTask> replanCandidate() {
            return tasks.values().stream().filter(task -> task.status == DagTaskStatus.FAILED)
                    .filter(task -> !"REPLAN_TERMINAL".equals(task.errorCode)).findFirst();
        }

        void applyReplan(com.example.agent.agent.contract.planning.CompiledPlan updatedPlan,
                         Set<String> replacedTaskIds) {
            this.plan = updatedPlan;
            taskSpecs.clear();
            updatedPlan.tasks().forEach(task -> taskSpecs.put(task.id(), task));
            for (String taskId : replacedTaskIds) {
                MutableTask previous = tasks.get(taskId);
                String idempotencyKey = previous == null ? "" : previous.idempotencyKey;
                tasks.put(taskId, new MutableTask(this, taskId, DagTaskStatus.PENDING, 0,
                        "", "", idempotencyKey, null, null, "", false, null));
            }
        }

        List<MutableTask> readyTasks() {
            return tasks.values().stream().filter(task -> task.status == DagTaskStatus.READY).toList();
        }

        void skipBlockedDownstream() {
            boolean changed;
            do {
                changed = false;
                for (MutableTask task : tasks.values()) {
                    if (task.status != DagTaskStatus.PENDING && task.status != DagTaskStatus.RETRYING) continue;
                    PlanTask spec = taskSpecs.get(task.taskId);
                    if (spec.dependencies().stream().map(tasks::get).anyMatch(dependency ->
                            dependency.status == DagTaskStatus.FAILED
                                    || dependency.status == DagTaskStatus.SKIPPED
                                    || dependency.status == DagTaskStatus.CANCELLED)) {
                        task.status = DagTaskStatus.SKIPPED;
                        task.message = "上游任务未成功，自动跳过";
                        changed = true;
                    }
                }
            } while (changed);
        }

        boolean allTerminal() {
            return tasks.values().stream().allMatch(task -> task.status == DagTaskStatus.COMPLETED
                    || task.status == DagTaskStatus.FAILED || task.status == DagTaskStatus.SKIPPED
                    || task.status == DagTaskStatus.CANCELLED);
        }

        boolean allGoalsAccepted() {
            LinkedHashSet<String> goalIds = new LinkedHashSet<>();
            plan.tasks().forEach(task -> goalIds.addAll(task.goalIds()));
            for (String goalId : goalIds) {
                boolean implementationPassed = plan.tasks().stream()
                        .filter(task -> task.goalIds().contains(goalId))
                        .filter(task -> !task.capabilityId().equals("planner.goal.accept"))
                        .anyMatch(task -> tasks.get(task.id()).status == DagTaskStatus.COMPLETED
                                && tasks.get(task.id()).evaluation != null
                                && tasks.get(task.id()).evaluation.decision() == EvaluationDecision.PASS);
                boolean acceptancePassed = plan.tasks().stream()
                        .filter(task -> task.goalIds().contains(goalId))
                        .filter(task -> task.capabilityId().equals("planner.goal.accept"))
                        .anyMatch(task -> tasks.get(task.id()).status == DagTaskStatus.COMPLETED
                                && tasks.get(task.id()).evaluation != null
                                && tasks.get(task.id()).evaluation.decision() == EvaluationDecision.PASS
                                && tasks.get(task.id()).result != null
                                && tasks.get(task.id()).result.output() instanceof Map<?, ?> output
                                && Boolean.TRUE.equals(output.get("accepted")));
                if (!implementationPassed || !acceptancePassed) return false;
            }
            return !goalIds.isEmpty();
        }

        void cancelRemaining() {
            tasks.values().stream().filter(task -> task.status != DagTaskStatus.COMPLETED
                            && task.status != DagTaskStatus.FAILED && task.status != DagTaskStatus.SKIPPED)
                    .forEach(task -> {
                        task.status = DagTaskStatus.CANCELLED;
                        task.message = "工作流已取消";
                    });
        }

        void failRemaining(String code, String message) {
            tasks.values().stream().filter(task -> task.status != DagTaskStatus.COMPLETED
                            && task.status != DagTaskStatus.FAILED && task.status != DagTaskStatus.SKIPPED)
                    .forEach(task -> {
                        task.status = DagTaskStatus.FAILED;
                        task.errorCode = code;
                        task.message = code + ": " + message;
                    });
        }

        DagExecutionSnapshot snapshot() {
            List<DagTaskState> states = tasks.values().stream().map(MutableTask::snapshot).toList();
            return new DagExecutionSnapshot(workflowId, principalId, conversationId, plan, status,
                    states, Map.copyOf(userInputs), createdAt, updatedAt, List.copyOf(replanRecords));
        }
    }

    private record ActiveRun(String principalId, AtomicBoolean cancel) {}

    private static final class MutableTask {
        private final MutableExecution owner;
        private final String taskId;
        private DagTaskStatus status;
        private int attempts;
        private String message;
        private String waitingSlot;
        private String idempotencyKey;
        private TypedTaskResult result;
        private TaskEvaluation evaluation;
        private String errorCode;
        private boolean replanPending;
        private ConfirmationRequest confirmationRequest;

        private MutableTask(MutableExecution owner, String taskId, DagTaskStatus status, int attempts,
                            String message, String waitingSlot, String idempotencyKey,
                            TypedTaskResult result, TaskEvaluation evaluation,
                            String errorCode, boolean replanPending,
                            ConfirmationRequest confirmationRequest) {
            this.owner = owner;
            this.taskId = taskId;
            this.status = status;
            this.attempts = attempts;
            this.message = message;
            this.waitingSlot = waitingSlot;
            this.idempotencyKey = idempotencyKey;
            this.result = result;
            this.evaluation = evaluation;
            this.errorCode = errorCode == null ? "" : errorCode;
            this.replanPending = replanPending;
            this.confirmationRequest = confirmationRequest;
        }

        DagTaskState snapshot() {
            return new DagTaskState(taskId, status, attempts, message, waitingSlot, idempotencyKey,
                    result, evaluation, errorCode, replanPending, confirmationRequest);
        }
    }
}
