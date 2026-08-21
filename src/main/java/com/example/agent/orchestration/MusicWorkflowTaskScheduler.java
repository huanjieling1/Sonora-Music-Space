package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicTaskEvaluation;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.contract.MusicTaskResult;
import com.example.agent.agent.main.MusicWorkflowChildAgent;
import com.example.agent.agent.response.GenericWorkflowResponse;
import com.example.agent.agent.response.GenericWorkflowResponseAgent;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.agent.orchestration.dag.DagExecutionCommand;
import com.example.agent.orchestration.dag.DagExecutionOptions;
import com.example.agent.orchestration.dag.DagExecutionSnapshot;
import com.example.agent.orchestration.dag.GenericDagExecutor;
import com.example.agent.orchestration.dag.GenericDagTaskExecutor;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Generic bounded scheduler. It resolves an agent by capability, owns attempts and applies
 * the supervisor decision; child agents never mutate workflow state directly.
 */
@Component
public final class MusicWorkflowTaskScheduler {
    private final MusicWorkflowChildAgentRegistry registry;
    private final GenericDagExecutor genericDagExecutor;
    private final GenericWorkflowResponseAgent genericResponseAgent;

    public MusicWorkflowTaskScheduler(MusicWorkflowChildAgentRegistry registry) {
        this(registry, null, null);
    }

    public MusicWorkflowTaskScheduler(MusicWorkflowChildAgentRegistry registry,
                                      GenericDagExecutor genericDagExecutor) {
        this(registry, genericDagExecutor, null);
    }

    @Autowired
    public MusicWorkflowTaskScheduler(MusicWorkflowChildAgentRegistry registry,
                                      GenericDagExecutor genericDagExecutor,
                                      GenericWorkflowResponseAgent genericResponseAgent) {
        this.registry = registry;
        this.genericDagExecutor = genericDagExecutor;
        this.genericResponseAgent = genericResponseAgent;
    }

    /** Executes any validated CompiledPlan through the shared DAG runtime. */
    public DagExecutionSnapshot executeCompiled(DagExecutionCommand command,
                                                GenericDagTaskExecutor executor) {
        return requireGeneric().execute(command, executor);
    }

    public DagExecutionSnapshot resumeCompiled(java.util.UUID workflowId, String principalId,
                                               java.util.Map<String, Object> userReplies,
                                               Object profileRoot,
                                               java.util.Set<String> allowedSensitiveProfilePaths,
                                               DagExecutionOptions options,
                                               GenericDagTaskExecutor executor) {
        return requireGeneric().resume(workflowId, principalId, userReplies, profileRoot,
                allowedSensitiveProfilePaths, options, executor);
    }

    public boolean cancelCompiled(java.util.UUID workflowId, String principalId) {
        return requireGeneric().cancel(workflowId, principalId);
    }

    public java.util.Optional<DagExecutionSnapshot> compiledSnapshot(java.util.UUID workflowId,
                                                                     String principalId) {
        return requireGeneric().snapshot(workflowId, principalId);
    }

    /** Produces one guarded response for a compiled arbitrary-goal workflow. */
    public GenericWorkflowResponse respondCompiled(
            com.example.agent.agent.contract.planning.UserGoalGraph graph,
            DagExecutionSnapshot snapshot) {
        if (genericResponseAgent == null) throw new IllegalStateException("通用 Response Agent 未配置");
        return genericResponseAgent.respond(graph, snapshot);
    }

    private GenericDagExecutor requireGeneric() {
        if (genericDagExecutor == null) throw new IllegalStateException("通用 DAG 执行器未配置");
        return genericDagExecutor;
    }

    public MusicScheduledTaskExecution executeVerified(
            MusicWorkflowRun run,
            MusicTaskInvocation initialInvocation,
            Function<MusicTaskResult, MusicTaskEvaluation> evaluator,
            BiFunction<MusicTaskEvaluation, Integer, MusicTaskInvocation> correction
    ) {
        if (run == null || initialInvocation == null || evaluator == null) {
            throw new IllegalArgumentException("调度任务所需上下文不完整");
        }
        String taskId = initialInvocation.task().id();
        MusicWorkflowChildAgent childAgent = registry.require(initialInvocation.task().capabilityId());
        run.assign(taskId, childAgent.descriptor().displayName());

        MusicTaskInvocation invocation = initialInvocation;
        MusicTaskResult result;
        MusicTaskEvaluation evaluation;
        do {
            run.start(taskId);
            try {
                result = childAgent.execute(invocation);
            } catch (RuntimeException exception) {
                result = new MusicTaskResult(taskId, false, null, List.of(),
                        exception.getMessage() == null ? "子 Agent 执行失败" : exception.getMessage(),
                        "CHILD_AGENT_EXCEPTION");
            }
            run.verifying(taskId);
            evaluation = evaluator.apply(result);
            if (evaluation == null) evaluation = MusicTaskEvaluation.fail("主 Agent 没有给出验收结论");

            switch (evaluation.decision()) {
                case PASS -> {
                    run.complete(taskId);
                    return new MusicScheduledTaskExecution(result, evaluation, invocation,
                            childAgent.descriptor().id());
                }
                case REVISE -> {
                    if (!run.canRetry(taskId) || correction == null) {
                        run.fail(taskId, evaluation.reason());
                        return new MusicScheduledTaskExecution(result,
                                MusicTaskEvaluation.fail(evaluation.reason()), invocation,
                                childAgent.descriptor().id());
                    }
                    run.retry(taskId, evaluation.reason());
                    MusicTaskInvocation corrected = correction.apply(evaluation,
                            run.snapshot().tasks().stream().filter(task -> task.id().equals(taskId))
                                    .findFirst().map(task -> task.attempts() + 1).orElse(2));
                    if (corrected != null) invocation = corrected;
                }
                case REPLAN -> {
                    run.replanning(taskId, evaluation.reason());
                    return new MusicScheduledTaskExecution(result, evaluation, invocation,
                            childAgent.descriptor().id());
                }
                case ASK_USER -> {
                    run.waitForUser(taskId, evaluation.correction().isBlank()
                            ? evaluation.reason() : evaluation.correction());
                    return new MusicScheduledTaskExecution(result, evaluation, invocation,
                            childAgent.descriptor().id());
                }
                case FAIL -> {
                    run.fail(taskId, evaluation.reason());
                    return new MusicScheduledTaskExecution(result, evaluation, invocation,
                            childAgent.descriptor().id());
                }
            }
        } while (true);
    }

    public MusicScheduledTaskExecution executeOnce(MusicWorkflowRun run, MusicTaskInvocation invocation) {
        return executeVerified(run, invocation,
                result -> result.successful() ? MusicTaskEvaluation.pass()
                        : MusicTaskEvaluation.fail(result.summary().isBlank()
                        ? "子 Agent 没有完成任务" : result.summary()), null);
    }
}
