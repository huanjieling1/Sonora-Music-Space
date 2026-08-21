package com.example.agent.orchestration.dag;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedEntityReference;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.goal.DeterministicMusicGoalParser;
import com.example.agent.agent.evaluation.TaskEvaluator;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidationContext;
import com.example.agent.agent.planner.PlanValidator;
import com.example.agent.agent.planner.ProfileFieldAccessPolicy;
import com.example.agent.agent.planner.SafeJsonPath;
import com.example.agent.agent.planner.ValueExpressionResolver;
import com.example.agent.skill.AgentSkillRegistry;
import com.example.agent.config.PlannerOperationsProperties;
import com.example.agent.orchestration.confirmation.ConfirmationManager;
import com.example.agent.orchestration.observability.PlannerEventType;
import com.example.agent.orchestration.observability.PlannerObservability;
import com.example.agent.orchestration.replanning.BoundedReplanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.example.agent.orchestration.replanning.ReplanProposal;
import com.example.agent.orchestration.replanning.ReplanRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GenericDagExecutorTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final DeterministicMusicGoalParser parser = new DeterministicMusicGoalParser();
    private final GenericPlanSynthesizer synthesizer = new GenericPlanSynthesizer(registry);
    private final PlanCompiler compiler = new PlanCompiler(new PlanValidator(registry));
    private final InMemoryDagExecutionPersistence persistence = new InMemoryDagExecutionPersistence();
    private final ArrayList<GenericDagExecutor> engines = new ArrayList<>();

    @AfterEach
    void closeExecutors() {
        engines.forEach(GenericDagExecutor::shutdown);
    }

    @Test
    void executesDynamicCompiledPlanInTopologicalOrderWithoutRouteHandlers() {
        CompiledPlan plan = compoundPlan();
        GenericDagExecutor engine = engine();
        ArrayList<String> calls = new ArrayList<>();
        UUID workflowId = UUID.randomUUID();

        DagExecutionSnapshot snapshot = engine.execute(command(workflowId, plan,
                        new DagExecutionOptions(4, 30, 5)), request -> {
                    calls.add(request.task().capabilityId());
                    return success(request);
                });

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(snapshot.tasks()).allMatch(task -> task.status() == DagTaskStatus.COMPLETED);
        assertThat(calls).containsExactly(
                "profile.artist.resolve", "planner.goal.accept",
                "qq.artist.lookup", "planner.goal.accept",
                "music.track.search", "planner.goal.accept",
                "music.queue.add", "planner.goal.accept");
        assertThat(engine.snapshot(workflowId, "user-1")).contains(snapshot);
    }

    @Test
    void emitsTaskTimingAndEvaluationEventsFromTheRealDagRuntime() {
        PlannerObservability observability = new PlannerObservability(
                new PlannerOperationsProperties(), new ObjectMapper().findAndRegisterModules());
        GenericDagExecutor engine = engine(observability);
        UUID workflowId = UUID.randomUUID();

        DagExecutionSnapshot snapshot = engine.execute(command(workflowId, singleSearchPlan(),
                new DagExecutionOptions(1, 30, 5)), this::success);

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(observability.recentEvents()).extracting(value -> value.type())
                .contains(PlannerEventType.TASK_STARTED, PlannerEventType.TASK_FINISHED,
                        PlannerEventType.TASK_EVALUATION);
        assertThat(observability.recentEvents().stream()
                .filter(value -> value.type() == PlannerEventType.TASK_FINISHED))
                .allSatisfy(value -> assertThat(value.attributes()).containsKey("durationMillis"));
    }

    @Test
    void runsIndependentReadOnlyTasksInParallelAndHonorsConcurrencyLimit() {
        CompiledPlan plan = parallelPlan();

        AtomicInteger runningTwo = new AtomicInteger();
        AtomicInteger maxTwo = new AtomicInteger();
        DagExecutionSnapshot parallel = engine().execute(command(UUID.randomUUID(), plan,
                new DagExecutionOptions(2, 30, 5)), request -> {
            if (request.task().capabilityId().equals("qq.artist.lookup")) {
                int current = runningTwo.incrementAndGet();
                maxTwo.accumulateAndGet(current, Math::max);
                Thread.sleep(150);
                runningTwo.decrementAndGet();
            }
            return success(request);
        });

        AtomicInteger runningOne = new AtomicInteger();
        AtomicInteger maxOne = new AtomicInteger();
        DagExecutionSnapshot limited = engine().execute(command(UUID.randomUUID(), plan,
                new DagExecutionOptions(1, 30, 5)), request -> {
            if (request.task().capabilityId().equals("qq.artist.lookup")) {
                int current = runningOne.incrementAndGet();
                maxOne.accumulateAndGet(current, Math::max);
                Thread.sleep(50);
                runningOne.decrementAndGet();
            }
            return success(request);
        });

        assertThat(parallel.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(limited.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(maxTwo).hasValue(2);
        assertThat(maxOne).hasValue(1);
    }

    @Test
    void retriesRetryableTaskWithinTaskBudget() {
        AtomicInteger searches = new AtomicInteger();
        DagExecutionSnapshot snapshot = engine().execute(command(UUID.randomUUID(), compoundPlan(),
                new DagExecutionOptions(2, 30, 5)), request -> {
            if (request.task().capabilityId().equals("music.track.search")
                    && searches.incrementAndGet() == 1) {
                return DagTaskOutcome.failure("TRANSIENT_PROVIDER", "临时失败", true);
            }
            return success(request);
        });

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(task(snapshot, "task-3-execute").attempts()).isEqualTo(2);
        assertThat(searches).hasValue(2);
    }

    @Test
    void appliesTaskTimeoutAndSkipsFailedDownstream() {
        CompiledPlan original = singleSearchPlan();
        ArrayList<PlanTask> tasks = new ArrayList<>(original.tasks());
        PlanTask first = tasks.get(0);
        tasks.set(0, new PlanTask(first.id(), first.title(), first.capabilityId(), first.goalIds(),
                first.inputs(), first.dependencies(), first.activationConditions(),
                first.acceptanceCriteria(), 1));
        CompiledPlan oneAttempt = new CompiledPlan(original.schemaVersion(), original.planId(),
                original.goalGraphId(), tasks, original.executionStages(), original.maxReplans());

        DagExecutionSnapshot snapshot = engine().execute(command(UUID.randomUUID(), oneAttempt,
                new DagExecutionOptions(1, 10, 1)), request -> {
            Thread.sleep(3000);
            return success(request);
        });

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.FAILED);
        assertThat(task(snapshot, "task-1-execute").status()).isEqualTo(DagTaskStatus.FAILED);
        assertThat(task(snapshot, "task-1-execute").message()).contains("TASK_TIMEOUT");
        assertThat(task(snapshot, "task-1-accept").status()).isEqualTo(DagTaskStatus.SKIPPED);
    }

    @Test
    void cancellationIsOwnerBoundAndInterruptsRunningTask() throws Exception {
        GenericDagExecutor engine = engine();
        UUID workflowId = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        CompletableFuture<DagExecutionSnapshot> running = CompletableFuture.supplyAsync(() ->
                engine.execute(command(workflowId, singleSearchPlan(),
                        new DagExecutionOptions(1, 30, 10)), request -> {
                    started.countDown();
                    Thread.sleep(5000);
                    return success(request);
                }));

        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(engine.cancel(workflowId, "other-user")).isFalse();
        assertThat(engine.cancel(workflowId, "user-1")).isTrue();
        DagExecutionSnapshot snapshot = running.get(3, TimeUnit.SECONDS);

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.CANCELLED);
        assertThat(snapshot.tasks()).allMatch(task -> task.status() == DagTaskStatus.CANCELLED
                || task.status() == DagTaskStatus.COMPLETED);
    }

    @Test
    void failedTaskAutomaticallySkipsItsEntireDownstreamSubgraph() {
        DagExecutionSnapshot snapshot = engine().execute(command(UUID.randomUUID(), compoundPlan(),
                new DagExecutionOptions(2, 30, 5)), request -> {
            if (request.task().capabilityId().equals("profile.artist.resolve")) {
                return DagTaskOutcome.failure("NO_PROFILE_ENTITY", "无法解析歌手", false);
            }
            return success(request);
        });

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.FAILED);
        assertThat(task(snapshot, "task-1-execute").status()).isEqualTo(DagTaskStatus.FAILED);
        assertThat(snapshot.tasks().stream().filter(task -> !task.taskId().equals("task-1-execute")))
                .allMatch(task -> task.status() == DagTaskStatus.SKIPPED);
    }

    @Test
    void successfulApiResponseCannotCompleteWorkflowWhenGoalAcceptanceIsFalse() {
        DagExecutionSnapshot snapshot = engine().execute(command(UUID.randomUUID(), singleSearchPlan(),
                new DagExecutionOptions(2, 30, 5)), request -> {
            if (request.task().capabilityId().equals("planner.goal.accept")) {
                AgentCapabilityDefinition capability = registry.find("planner.goal.accept").orElseThrow();
                return DagTaskOutcome.success(TypedTaskResult.success(request.task().id(),
                        capability.outputSchema(), Map.of("accepted", false,
                                "findings", List.of("返回数量不满足目标")),
                        "RUNTIME", "", List.of(), List.of("ev-rejected")));
            }
            return success(request);
        });

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.FAILED);
        assertThat(task(snapshot, "task-1-accept").status()).isEqualTo(DagTaskStatus.FAILED);
        assertThat(task(snapshot, "task-1-accept").message()).contains("TASK_EVALUATION_REPLAN");
        assertThat(task(snapshot, "task-1-accept").evaluation().decision().name()).isEqualTo("REPLAN");
    }

    @Test
    void successfulMutationResponseCannotCompleteWithoutProvenStateChange() {
        DagExecutionSnapshot snapshot = engine().execute(command(UUID.randomUUID(), compoundPlan(),
                new DagExecutionOptions(2, 30, 5)), request -> {
            if (request.task().capabilityId().equals("music.queue.add")) {
                AgentCapabilityDefinition capability = registry.find("music.queue.add").orElseThrow();
                return DagTaskOutcome.success(TypedTaskResult.success(request.task().id(),
                        capability.outputSchema(), Map.of("success", false, "queuedCount", 0),
                        "RUNTIME", "", List.of(), List.of("ev-no-change")));
            }
            return success(request);
        });

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.FAILED);
        assertThat(task(snapshot, "task-4-execute").status()).isEqualTo(DagTaskStatus.FAILED);
        assertThat(task(snapshot, "task-4-execute").evaluation().findings())
                .extracting(com.example.agent.agent.evaluation.EvaluationFinding::code)
                .contains("STATE_CHANGE_NOT_PROVEN");
    }

    @Test
    void replansOnlyFailedSubgraphAndDoesNotRepeatAcceptedIndependentTask() {
        CompiledPlan plan = parallelPlan();
        AtomicInteger failedCalls = new AtomicInteger();
        AtomicInteger preservedCalls = new AtomicInteger();
        AtomicReference<ReplanRequest> replanRequest = new AtomicReference<>();
        ReplanningDagTaskExecutor executor = new ReplanningDagTaskExecutor() {
            @Override
            public DagTaskOutcome execute(DagTaskExecutionRequest request) {
                if (request.task().id().equals("task-2-execute")) preservedCalls.incrementAndGet();
                if (request.task().id().equals("task-1-execute")
                        && request.task().capabilityId().equals("qq.artist.lookup")
                        && failedCalls.incrementAndGet() == 1) {
                    return DagTaskOutcome.failure("PROVIDER_UNAVAILABLE", "QQ artist provider unavailable", false);
                }
                return success(request);
            }

            @Override
            public ReplanProposal replan(ReplanRequest request) {
                replanRequest.set(request);
                List<PlanTask> replacement = request.currentPlan().tasks().stream()
                        .filter(task -> request.failedSubgraphTaskIds().contains(task.id()))
                        .map(task -> task.id().equals("task-1-execute")
                                ? new PlanTask(task.id(), "从画像解析备用歌手", "profile.artist.resolve",
                                task.goalIds(), Map.of("profile", ValueExpression.profileValue(
                                ValueType.OBJECT, "$.musicProfile")), task.dependencies(),
                                task.activationConditions(), task.acceptanceCriteria(), 1)
                                : task).toList();
                return ReplanProposal.replace(replacement, "切换到画像实体解析能力");
            }
        };

        DagExecutionSnapshot snapshot = engine().execute(command(UUID.randomUUID(), plan,
                new DagExecutionOptions(2, 30, 5)), executor);

        assertThat(snapshot.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(snapshot.replanRecords()).hasSize(1);
        assertThat(snapshot.replanRecords().get(0).outcome().name()).isEqualTo("APPLIED");
        assertThat(replanRequest.get().errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(replanRequest.get().preservedResults()).containsKey("task-2-execute");
        assertThat(replanRequest.get().acceptanceCriteria()).containsKey("task-1-execute");
        assertThat(preservedCalls).hasValue(1);
        assertThat(snapshot.plan().tasks().stream().filter(task -> task.id().equals("task-1-execute"))
                .findFirst().orElseThrow().capabilityId()).isEqualTo("profile.artist.resolve");
    }

    @Test
    void waitsForUserThenRestoresFromPersistenceAndReusesSideEffectIdempotencyKey() {
        UUID workflowId = UUID.randomUUID();
        AtomicInteger queueCalls = new AtomicInteger();
        List<String> keys = new ArrayList<>();
        GenericDagExecutor firstProcess = engine();
        DagExecutionSnapshot waiting = firstProcess.execute(command(workflowId, compoundPlan(),
                new DagExecutionOptions(2, 30, 5)), request -> {
            if (request.task().capabilityId().equals("music.queue.add")) {
                keys.add(request.idempotencyKey());
                queueCalls.incrementAndGet();
                return DagTaskOutcome.waiting("queue.confirm", "确认加入播放队列吗？");
            }
            return success(request);
        });

        assertThat(waiting.status()).isEqualTo(DagWorkflowStatus.WAITING_USER);
        assertThat(task(waiting, "task-4-execute").waitingSlot()).isEqualTo("queue.confirm");
        assertThat(task(waiting, "task-4-execute").attempts()).isZero();
        assertThat(persistence.load(workflowId, "user-1")).contains(waiting);

        GenericDagExecutor restoredProcess = engine();
        DagExecutionSnapshot completed = restoredProcess.resume(workflowId, "user-1",
                Map.of("queue.confirm", true), profileRoot(), Set.of(),
                new DagExecutionOptions(2, 30, 5), request -> {
                    if (request.task().capabilityId().equals("music.queue.add")) {
                        keys.add(request.idempotencyKey());
                        queueCalls.incrementAndGet();
                    }
                    return success(request);
                });

        assertThat(completed.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(queueCalls).hasValue(2);
        assertThat(keys).hasSize(2).doesNotContain("");
        assertThat(keys.get(0)).isEqualTo(keys.get(1));
        assertThat(task(completed, "task-4-execute").attempts()).isEqualTo(1);
        assertThat(restoredProcess.snapshot(workflowId, "user-1")).contains(completed);
    }

    @Test
    void pausesBeforeMutationPersistsInputsAndContinuesOnlyAfterApproval() {
        UUID workflowId = UUID.randomUUID();
        AtomicInteger queueCalls = new AtomicInteger();
        GenericDagExecutor first = engine();
        DagExecutionSnapshot waiting = first.execute(commandWithoutConfirmation(workflowId, compoundPlan()),
                request -> {
                    if (request.task().capabilityId().equals("music.queue.add")) queueCalls.incrementAndGet();
                    return success(request);
                });

        DagTaskState queue = task(waiting, "task-4-execute");
        assertThat(waiting.status()).isEqualTo(DagWorkflowStatus.WAITING_USER);
        assertThat(queueCalls).hasValue(0);
        assertThat(queue.attempts()).isZero();
        assertThat(queue.confirmationRequest()).isNotNull();
        assertThat(queue.confirmationRequest().pendingInputs()).containsKey("tracks");
        assertThat(queue.confirmationRequest().idempotencyKey()).isNotBlank();

        GenericDagExecutor restored = engine();
        DagExecutionSnapshot completed = restored.resume(workflowId, "user-1",
                Map.of(queue.confirmationRequest().replySlot(), true), profileRoot(), Set.of(),
                new DagExecutionOptions(2, 30, 5), request -> {
                    if (request.task().capabilityId().equals("music.queue.add")) queueCalls.incrementAndGet();
                    return success(request);
                });

        assertThat(completed.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(queueCalls).hasValue(1);
        assertThat(task(completed, "task-4-execute").confirmationRequest().status().name())
                .isEqualTo("APPROVED");
    }

    @Test
    void rejectionSkipsMutationAndItsDownstreamBranch() {
        UUID workflowId = UUID.randomUUID();
        AtomicInteger queueCalls = new AtomicInteger();
        GenericDagExecutor first = engine();
        DagExecutionSnapshot waiting = first.execute(commandWithoutConfirmation(workflowId, compoundPlan()),
                request -> {
                    if (request.task().capabilityId().equals("music.queue.add")) queueCalls.incrementAndGet();
                    return success(request);
                });
        DagTaskState queue = task(waiting, "task-4-execute");

        DagExecutionSnapshot rejected = engine().resume(workflowId, "user-1",
                Map.of(queue.confirmationRequest().replySlot(), false), profileRoot(), Set.of(),
                new DagExecutionOptions(2, 30, 5), request -> {
                    if (request.task().capabilityId().equals("music.queue.add")) queueCalls.incrementAndGet();
                    return success(request);
                });

        assertThat(rejected.status()).isEqualTo(DagWorkflowStatus.FAILED);
        assertThat(task(rejected, "task-4-execute").status()).isEqualTo(DagTaskStatus.SKIPPED);
        assertThat(task(rejected, "task-4-accept").status()).isEqualTo(DagTaskStatus.SKIPPED);
        assertThat(queueCalls).hasValue(0);
    }

    @Test
    void supplementsMissingInputThenRebindsAndExecutesTask() {
        UUID workflowId = UUID.randomUUID();
        AtomicReference<Object> resolvedQuery = new AtomicReference<>();
        DagExecutionSnapshot waiting = engine().execute(commandWithoutConfirmation(workflowId,
                missingInputPlan()), request -> {
            if (request.task().id().equals("missing-search")) {
                resolvedQuery.set(request.resolvedInputs().get("query"));
            }
            return success(request);
        });

        assertThat(waiting.status()).isEqualTo(DagWorkflowStatus.WAITING_USER);
        assertThat(task(waiting, "missing-search").waitingSlot()).isEqualTo("search.query");

        DagExecutionSnapshot completed = engine().resume(workflowId, "user-1",
                Map.of("search.query", "Mili"), profileRoot(), Set.of(),
                new DagExecutionOptions(1, 30, 5), request -> {
                    if (request.task().id().equals("missing-search")) {
                        resolvedQuery.set(request.resolvedInputs().get("query"));
                    }
                    return success(request);
                });

        assertThat(completed.status()).isEqualTo(DagWorkflowStatus.COMPLETED);
        assertThat(resolvedQuery).hasValue("Mili");
    }

    private GenericDagExecutor engine() {
        GenericDagExecutor value = new GenericDagExecutor(registry,
                new ValueExpressionResolver(new SafeJsonPath(new ObjectMapper().findAndRegisterModules()),
                        new ProfileFieldAccessPolicy()), persistence);
        engines.add(value);
        return value;
    }

    private GenericDagExecutor engine(PlannerObservability observability) {
        GenericDagExecutor value = new GenericDagExecutor(registry,
                new ValueExpressionResolver(new SafeJsonPath(new ObjectMapper().findAndRegisterModules()),
                        new ProfileFieldAccessPolicy()), persistence,
                new TaskEvaluator(new SafeJsonPath(new ObjectMapper().findAndRegisterModules())),
                new BoundedReplanner(registry, new ObjectMapper().findAndRegisterModules()),
                new ConfirmationManager(), observability);
        engines.add(value);
        return value;
    }

    private CompiledPlan compoundPlan() {
        UserGoalGraph graph = parser.parse("找出我最喜欢的歌手资料，再推荐三首他的歌并加入队列");
        return compiler.compile(graph, synthesizer.synthesize(graph),
                PlanValidationContext.standard("user-1"));
    }

    private CompiledPlan parallelPlan() {
        UserGoalGraph graph = parser.parse("搜索周杰伦的资料，同时搜索林俊杰的资料");
        return compiler.compile(graph, synthesizer.synthesize(graph),
                PlanValidationContext.standard("user-1"));
    }

    private CompiledPlan singleSearchPlan() {
        AcceptanceCriterion criterion = new AcceptanceCriterion("tracks", AcceptanceCriterion.Type.OUTPUT_PRESENT,
                "$.tracks", null, true, "必须返回歌曲", Map.of());
        GoalNode goal = new GoalNode("search", "搜索 Mili 歌曲", GoalOperation.SEARCH,
                GoalTargetType.TRACK, Map.of("trackTitle", ValueExpression.literal(ValueType.STRING, "Mili")),
                List.of(), List.of(criterion), List.of(), false);
        UserGoalGraph graph = new UserGoalGraph("1.0", UUID.randomUUID(), "搜索 Mili 歌曲",
                List.of(goal), List.of());
        return compiler.compile(graph, synthesizer.synthesize(graph),
                PlanValidationContext.standard("user-1"));
    }

    private CompiledPlan missingInputPlan() {
        AcceptanceCriterion output = new AcceptanceCriterion("tracks", AcceptanceCriterion.Type.OUTPUT_PRESENT,
                "$.tracks", null, true, "必须返回歌曲", Map.of());
        PlanTask search = new PlanTask("missing-search", "搜索歌曲", "music.track.search",
                List.of("missing-goal"), Map.of("query", ValueExpression.userInput(
                ValueType.STRING, "search.query", true)), List.of(), List.of(output), 1);
        PlanTask accept = new PlanTask("missing-accept", "验收搜索", "planner.goal.accept",
                List.of("missing-goal"), Map.of(
                "goalId", ValueExpression.literal(ValueType.STRING, "missing-goal"),
                "result", ValueExpression.taskOutput(ValueType.OBJECT, "missing-search", "$"),
                "criteria", ValueExpression.literal(ValueType.ARRAY, List.of())),
                List.of("missing-search"), List.of(new AcceptanceCriterion("accepted",
                AcceptanceCriterion.Type.GOAL_COVERAGE, "$.accepted",
                ValueExpression.literal(ValueType.BOOLEAN, true), true, "必须验收", Map.of())), 1);
        return new CompiledPlan("1.0", UUID.randomUUID(), UUID.randomUUID(), List.of(search, accept),
                List.of(List.of("missing-search"), List.of("missing-accept")), 0);
    }

    private static DagExecutionCommand commandWithoutConfirmation(UUID workflowId, CompiledPlan plan) {
        return new DagExecutionCommand(workflowId, "user-1", UUID.randomUUID().toString(), plan,
                Map.of(), profileRoot(), Set.of(), new DagExecutionOptions(2, 30, 5));
    }

    private static DagExecutionCommand command(UUID workflowId, CompiledPlan plan,
                                               DagExecutionOptions options) {
        return new DagExecutionCommand(workflowId, "user-1", UUID.randomUUID().toString(), plan,
                Map.of("confirmation.task-4-execute", true), profileRoot(), Set.of(), options);
    }

    private static Object profileRoot() {
        return Map.of("musicProfile", Map.of("stage", "MATURE", "topArtists", List.of("Mili")));
    }

    private DagTaskOutcome success(DagTaskExecutionRequest request) {
        String capabilityId = request.task().capabilityId();
        AgentCapabilityDefinition capability = registry.find(capabilityId).orElseThrow();
        Object output;
        String resourceId = "";
        List<TypedEntityReference> entities = List.of();
        switch (capabilityId) {
            case "profile.artist.resolve" -> {
                output = Map.of("artistName", "Mili", "confidence", 0.99,
                        "evidenceIds", List.of("profile-evidence"));
                entities = List.of(new TypedEntityReference(GoalTargetType.ARTIST,
                        "Mili", "PROFILE", "artist:mili"));
            }
            case "qq.artist.lookup" -> {
                String artist = String.valueOf(request.resolvedInputs().get("artistName"));
                output = Map.of("artistId", "artist:" + artist, "canonicalName", artist,
                        "profile", Map.of("name", artist), "provider", "QQ_MUSIC");
                resourceId = "artist:" + artist;
                entities = List.of(new TypedEntityReference(GoalTargetType.ARTIST,
                        artist, "QQ_MUSIC", resourceId));
            }
            case "music.track.search" -> {
                output = Map.of("searchId", "search-1", "tracks", List.of(Map.of(
                                "id", "track-1", "title", "world.execute(me);", "artist", "Mili")),
                        "provider", "QQ_MUSIC");
                resourceId = "search-1";
                entities = List.of(new TypedEntityReference(GoalTargetType.TRACK,
                        "world.execute(me);", "QQ_MUSIC", "track-1"));
            }
            case "music.queue.add" -> output = Map.of("success", true, "queuedCount", 1);
            case "planner.goal.accept" -> output = Map.of("accepted", true, "findings", List.of());
            default -> throw new IllegalStateException("测试未实现能力：" + capabilityId);
        }
        TypedTaskResult result = TypedTaskResult.success(request.task().id(), capability.outputSchema(),
                output, capabilityId.equals("profile.artist.resolve") ? "PROFILE" : "RUNTIME",
                resourceId, entities, List.of("evidence:" + request.task().id()));
        return DagTaskOutcome.success(result);
    }

    private static DagTaskState task(DagExecutionSnapshot snapshot, String taskId) {
        return snapshot.tasks().stream().filter(task -> task.taskId().equals(taskId))
                .findFirst().orElseThrow();
    }
}
