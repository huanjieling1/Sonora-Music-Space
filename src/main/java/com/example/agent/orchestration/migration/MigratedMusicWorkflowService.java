package com.example.agent.orchestration.migration;

import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidationContext;
import com.example.agent.agent.profile.MusicRecommendationProfileAgent;
import com.example.agent.agent.response.GenericWorkflowResponseAgent;
import com.example.agent.orchestration.dag.DagExecutionCommand;
import com.example.agent.orchestration.dag.DagExecutionOptions;
import com.example.agent.orchestration.dag.DagExecutionPersistence;
import com.example.agent.orchestration.dag.DagExecutionSnapshot;
import com.example.agent.orchestration.dag.DagWorkflowStatus;
import com.example.agent.orchestration.dag.GenericDagExecutor;
import com.example.agent.orchestration.observability.PlannerObservability;
import com.example.agent.orchestration.observability.PlannerRolloutBlockedException;
import com.example.agent.orchestration.observability.PlannerRolloutPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

/** Production-capable dynamic path used during migration and by future arbitrary multi-intent entry points. */
@Service
public final class MigratedMusicWorkflowService {
    private final LegacyRouteGoalGraphAdapter compatibility;
    private final GenericPlanSynthesizer synthesizer;
    private final PlanCompiler compiler;
    private final GenericDagExecutor dagExecutor;
    private final MigratedMusicCapabilityExecutor capabilityExecutor;
    private final MigratedMusicExecutionContextRegistry contexts;
    private final MusicRecommendationProfileAgent profileAgent;
    private final GenericWorkflowResponseAgent responseAgent;
    private final ObjectMapper objectMapper;
    private final PlannerObservability observability;
    private final PlannerRolloutPolicy rolloutPolicy;
    private final DagExecutionPersistence persistence;
    private final MigratedMusicActionRegistry migratedActions;

    public MigratedMusicWorkflowService(LegacyRouteGoalGraphAdapter compatibility,
                                        GenericPlanSynthesizer synthesizer,
                                        PlanCompiler compiler,
                                        GenericDagExecutor dagExecutor,
                                        MigratedMusicCapabilityExecutor capabilityExecutor,
                                        MigratedMusicExecutionContextRegistry contexts,
                                        MusicRecommendationProfileAgent profileAgent,
                                        GenericWorkflowResponseAgent responseAgent,
                                        ObjectMapper objectMapper,
                                        PlannerObservability observability,
                                        PlannerRolloutPolicy rolloutPolicy,
                                        DagExecutionPersistence persistence,
                                        MigratedMusicActionRegistry migratedActions) {
        this.compatibility = compatibility;
        this.synthesizer = synthesizer;
        this.compiler = compiler;
        this.dagExecutor = dagExecutor;
        this.capabilityExecutor = capabilityExecutor;
        this.contexts = contexts;
        this.profileAgent = profileAgent;
        this.responseAgent = responseAgent;
        this.objectMapper = objectMapper;
        this.observability = observability;
        this.rolloutPolicy = rolloutPolicy;
        this.persistence = persistence;
        this.migratedActions = migratedActions;
    }

    public MigratedMusicWorkflowResult executeLegacy(MusicAgentTurn turn,
                                                      MusicGoalUnderstanding understanding,
                                                      Map<String, Object> userInputs) {
        UserGoalGraph graph = compatibility.adapt(turn, understanding)
                .orElseThrow(() -> new IllegalArgumentException("该旧路由尚未迁移：" + understanding.route()));
        return executeGraph(turn, graph, understanding.followUpPlan(), userInputs);
    }

    public MigratedMusicWorkflowResult executeGraph(MusicAgentTurn turn, UserGoalGraph graph,
                                                     MusicTurnPlan followUpPlan,
                                                     Map<String, Object> userInputs) {
        PreparedMigratedMusicWorkflow prepared = prepare(turn, graph, followUpPlan);
        if (prepared.rollout().action() != PlannerRolloutPolicy.Action.EXECUTE) {
            throw new PlannerRolloutBlockedException(prepared.rollout());
        }
        return execute(prepared, userInputs);
    }

    /** Compile and decide rollout without profile reads or capability side effects. */
    public PreparedMigratedMusicWorkflow prepare(MusicAgentTurn turn, UserGoalGraph graph,
                                                  MusicTurnPlan followUpPlan) {
        if (turn == null || graph == null) throw new IllegalArgumentException("动态迁移请求不完整");
        UUID workflowId = UUID.randomUUID();
        final com.example.agent.agent.contract.planning.CompiledPlan plan;
        try {
            var draft = synthesizer.synthesize(graph);
            PlanValidationContext validation = new PlanValidationContext(String.valueOf(turn.userId()),
                    true, true, true,
                    graph.goals().stream().filter(goal -> goal.requiresConfirmation()).map(goal -> goal.id())
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    Set.of(CapabilitySideEffect.READ_ONLY, CapabilitySideEffect.REVERSIBLE_SESSION,
                            CapabilitySideEffect.PERSISTENT_MUTATION), 200, 300, 50);
            plan = compiler.compile(graph, draft, validation);
        } catch (RuntimeException exception) {
            observability.planningRejected(graph, exception, workflowId.toString());
            throw exception;
        }
        observability.compiled(graph, plan, workflowId.toString());
        PlannerRolloutPolicy.Decision rollout = rolloutPolicy.decide(plan);
        observability.rollout(workflowId.toString(), rollout);
        return new PreparedMigratedMusicWorkflow(workflowId, turn, graph, followUpPlan, plan, rollout);
    }

    /** Execute a previously validated plan. No caller may fall back to legacy after this method starts. */
    public MigratedMusicWorkflowResult execute(PreparedMigratedMusicWorkflow prepared,
                                               Map<String, Object> userInputs) {
        if (prepared == null) throw new IllegalArgumentException("动态工作流准备结果不能为空");
        if (prepared.rollout().action() != PlannerRolloutPolicy.Action.EXECUTE) {
            throw new PlannerRolloutBlockedException(prepared.rollout());
        }
        MusicAgentTurn turn = prepared.turn();
        UserGoalGraph graph = prepared.goalGraph();
        UUID workflowId = prepared.workflowId();
        UserTasteContext taste = profileAgent.prepare(turn);
        contexts.register(workflowId, new MigratedMusicExecutionContextRegistry.Context(
                turn, taste, prepared.followUpPlan(), graph));
        DagExecutionSnapshot snapshot;
        try {
            snapshot = dagExecutor.execute(new DagExecutionCommand(workflowId, String.valueOf(turn.userId()),
                    turn.conversationId().toString(), prepared.plan(), userInputs, profileRoot(taste), Set.of(),
                    DagExecutionOptions.defaults()), capabilityExecutor);
        } catch (RuntimeException exception) {
            contexts.release(workflowId);
            throw exception;
        }
        var response = responseAgent.respond(graph, snapshot);
        if (snapshot.status() == DagWorkflowStatus.WAITING_USER) {
            persistence.saveResumeContext(workflowId, String.valueOf(turn.userId()),
                    write(new ResumeContext(turn, prepared.followUpPlan(), graph)));
        } else {
            contexts.release(workflowId);
        }
        var actions = migratedActions.drainAccepted(workflowId, acceptedTasks(snapshot));
        if (snapshot.status() != DagWorkflowStatus.WAITING_USER) migratedActions.release(workflowId);
        return new MigratedMusicWorkflowResult(graph, snapshot, response, actions);
    }

    public MigratedMusicWorkflowResult resume(UUID workflowId, long userId,
                                              Map<String, Object> replies) {
        var persisted = dagExecutor.snapshot(workflowId, String.valueOf(userId))
                .orElseThrow(() -> new IllegalArgumentException("找不到当前用户的工作流：" + workflowId));
        var context = contexts.find(workflowId, String.valueOf(userId))
                .orElseGet(() -> restoreContext(workflowId, userId));
        PlannerRolloutPolicy.Decision rollout = rolloutPolicy.decide(persisted.plan());
        observability.rollout(workflowId.toString(), rollout);
        if (rollout.action() != PlannerRolloutPolicy.Action.EXECUTE) {
            throw new PlannerRolloutBlockedException(rollout);
        }
        DagExecutionSnapshot snapshot = dagExecutor.resume(workflowId, String.valueOf(userId), replies,
                profileRoot(context.tasteContext()), Set.of(), DagExecutionOptions.defaults(), capabilityExecutor);
        var response = responseAgent.respond(context.goalGraph(), snapshot);
        if (snapshot.status() != DagWorkflowStatus.WAITING_USER) contexts.release(workflowId);
        if (snapshot.status() == DagWorkflowStatus.WAITING_USER) {
            persistence.saveResumeContext(workflowId, String.valueOf(userId),
                    write(new ResumeContext(context.turn(), context.followUpPlan(), context.goalGraph())));
        }
        var actions = migratedActions.drainAccepted(workflowId, acceptedTasks(snapshot));
        if (snapshot.status() != DagWorkflowStatus.WAITING_USER) migratedActions.release(workflowId);
        return new MigratedMusicWorkflowResult(context.goalGraph(), snapshot, response, actions);
    }

    public Optional<MigratedMusicWorkflowResult> resumeWaiting(MusicAgentTurn replyTurn) {
        if (replyTurn == null) return Optional.empty();
        return persistence.findLatestWaiting(String.valueOf(replyTurn.userId()),
                        replyTurn.conversationId().toString())
                .map(snapshot -> resume(snapshot.workflowId(), replyTurn.userId(), replies(snapshot, replyTurn.request())));
    }

    private MigratedMusicExecutionContextRegistry.Context restoreContext(UUID workflowId, long userId) {
        ResumeContext stored = persistence.loadResumeContext(workflowId, String.valueOf(userId))
                .map(value -> read(value, ResumeContext.class))
                .orElseThrow(() -> new IllegalStateException("迁移工作流恢复上下文不存在：" + workflowId));
        UserTasteContext taste = profileAgent.prepare(stored.turn());
        var restored = new MigratedMusicExecutionContextRegistry.Context(
                stored.turn(), taste, stored.followUpPlan(), stored.goalGraph());
        contexts.register(workflowId, restored);
        return restored;
    }

    private static Map<String, Object> replies(DagExecutionSnapshot snapshot, String reply) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        snapshot.tasks().stream().filter(task -> task.status() == com.example.agent.orchestration.dag.DagTaskStatus.WAITING_USER)
                .filter(task -> !task.waitingSlot().isBlank()).forEach(task -> {
                    Object value = task.confirmationRequest() == null ? reply : normalizeConfirmation(reply);
                    values.put(task.waitingSlot(), value);
                });
        return Map.copyOf(values);
    }

    private static Object normalizeConfirmation(String reply) {
        String value = reply == null ? "" : reply.strip().toLowerCase(java.util.Locale.ROOT);
        if (Set.of("可以", "好", "好的", "执行", "继续", "同意", "确认", "批准", "yes", "true")
                .contains(value)) return true;
        if (Set.of("不要", "不用", "算了", "停止", "拒绝", "取消", "no", "false")
                .contains(value)) return false;
        return reply;
    }

    private static Set<String> acceptedTasks(DagExecutionSnapshot snapshot) {
        return snapshot.tasks().stream()
                .filter(task -> task.status() == com.example.agent.orchestration.dag.DagTaskStatus.COMPLETED)
                .filter(task -> task.result() != null && task.result().successful())
                .filter(task -> task.evaluation() != null
                        && task.evaluation().decision() == com.example.agent.agent.evaluation.EvaluationDecision.PASS)
                .map(com.example.agent.orchestration.dag.DagTaskState::taskId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("无法持久化迁移恢复上下文", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("无法恢复迁移工作流上下文", exception);
        }
    }

    private Map<String, Object> profileRoot(UserTasteContext taste) {
        LinkedHashMap<String, Object> profile = objectMapper.convertValue(taste,
                new TypeReference<LinkedHashMap<String, Object>>() {});
        return Map.of("musicProfile", profile);
    }

    public record ResumeContext(MusicAgentTurn turn, MusicTurnPlan followUpPlan, UserGoalGraph goalGraph) {
        public ResumeContext {
            if (turn == null || goalGraph == null) throw new IllegalArgumentException("恢复上下文不完整");
            followUpPlan = followUpPlan == null ? MusicTurnPlan.none() : followUpPlan;
        }
    }
}
