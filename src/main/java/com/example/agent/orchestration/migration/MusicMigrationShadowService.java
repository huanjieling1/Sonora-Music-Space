package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidationContext;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.orchestration.observability.PlannerObservability;
import com.example.agent.orchestration.observability.PlannerRolloutPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Compiles the dynamic equivalent without executing tools, then compares it with the legacy real outcome. */
@Component
public final class MusicMigrationShadowService {
    private static final Logger log = LoggerFactory.getLogger(MusicMigrationShadowService.class);
    private static final int HISTORY_LIMIT = 200;
    private static final Map<MusicAgentRoute, Set<AgentActionType>> EXPECTED_EVIDENCE = evidence();

    private final LegacyRouteGoalGraphAdapter adapter;
    private final GenericPlanSynthesizer synthesizer;
    private final PlanCompiler compiler;
    private final PlannerObservability observability;
    private final PlannerRolloutPolicy rolloutPolicy;
    private final Deque<MigrationShadowComparison> history = new ArrayDeque<>();

    public MusicMigrationShadowService(LegacyRouteGoalGraphAdapter adapter,
                                       GenericPlanSynthesizer synthesizer,
                                       PlanCompiler compiler) {
        this(adapter, synthesizer, compiler, PlannerObservability.noop(), null);
    }

    @Autowired
    public MusicMigrationShadowService(LegacyRouteGoalGraphAdapter adapter,
                                       GenericPlanSynthesizer synthesizer,
                                       PlanCompiler compiler,
                                       PlannerObservability observability,
                                       PlannerRolloutPolicy rolloutPolicy) {
        this.adapter = adapter;
        this.synthesizer = synthesizer;
        this.compiler = compiler;
        this.observability = observability;
        this.rolloutPolicy = rolloutPolicy;
    }

    public Optional<PreparedShadow> prepare(MusicAgentTurn turn, MusicGoalUnderstanding understanding) {
        if (rolloutPolicy != null && !rolloutPolicy.shadowEnabled()) return Optional.empty();
        UserGoalGraph graph = null;
        try {
            Optional<UserGoalGraph> projected = adapter.adapt(turn, understanding);
            if (projected.isEmpty()) return Optional.empty();
            graph = projected.get();
            var draft = synthesizer.synthesize(graph);
            PlanValidationContext base = PlanValidationContext.standard(String.valueOf(turn.userId()));
            PlanValidationContext context = new PlanValidationContext(base.principalId(), true, true, true,
                    graph.goals().stream().filter(goal -> goal.requiresConfirmation())
                            .map(goal -> goal.id()).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    base.allowedSideEffects(), base.maxCostUnits(), base.maxDurationSeconds(),
                    base.maxTotalAttempts());
            CompiledPlan plan = compiler.compile(graph, draft, context);
            observability.compiled(graph, plan, "shadow:" + turn.conversationId());
            return Optional.of(new PreparedShadow(understanding.route(), graph, plan, ""));
        } catch (RuntimeException exception) {
            observability.planningRejected(graph, exception, "shadow:" + turn.conversationId());
            log.warn("Dynamic migration shadow could not compile route={}: {}",
                    understanding.route(), exception.getMessage());
            if (graph == null) return Optional.empty();
            return Optional.of(new PreparedShadow(understanding.route(), graph, null,
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    public MigrationShadowComparison compare(PreparedShadow prepared, MusicAgentWorkflowState legacy) {
        ArrayList<String> findings = new ArrayList<>();
        if (prepared.plan() == null) findings.add("DYNAMIC_PLAN_NOT_COMPILED: " + prepared.error());
        boolean legacySuccessful = legacy != null && (legacy.executionResult() == null
                ? legacy.answer() != null && !legacy.answer().isBlank()
                : legacy.executionResult().successful());
        if (!legacySuccessful) findings.add("LEGACY_EXECUTION_NOT_SUCCESSFUL");
        Set<AgentActionType> expected = EXPECTED_EVIDENCE.getOrDefault(prepared.route(), Set.of());
        Set<AgentActionType> actual = legacy == null || legacy.executionResult() == null
                ? Set.of() : legacy.executionResult().evidenceTypes();
        if (legacySuccessful && !actual.containsAll(expected)) {
            findings.add("LEGACY_EVIDENCE_MISMATCH expected=" + expected + " actual=" + actual);
        }
        List<String> capabilities = prepared.plan() == null ? List.of() : prepared.plan().tasks().stream()
                .map(task -> task.capabilityId()).filter(id -> !"planner.goal.accept".equals(id)).distinct().toList();
        MigrationShadowComparison comparison = new MigrationShadowComparison(UUID.randomUUID(), Instant.now(),
                prepared.route(), prepared.graph().graphId(),
                prepared.plan() == null ? null : prepared.plan().planId(), capabilities,
                legacySuccessful, findings.isEmpty(), findings);
        remember(comparison);
        if (comparison.compatible()) {
            log.debug("Migration shadow matched route={} capabilities={}", prepared.route(), capabilities);
        } else {
            log.warn("Migration shadow mismatch route={} findings={}", prepared.route(), findings);
        }
        return comparison;
    }

    public synchronized List<MigrationShadowComparison> recent() {
        return List.copyOf(history);
    }

    private synchronized void remember(MigrationShadowComparison comparison) {
        history.addFirst(comparison);
        while (history.size() > HISTORY_LIMIT) history.removeLast();
    }

    private static Map<MusicAgentRoute, Set<AgentActionType>> evidence() {
        EnumMap<MusicAgentRoute, Set<AgentActionType>> values = new EnumMap<>(MusicAgentRoute.class);
        values.put(MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE,
                Set.of(AgentActionType.SHOW_QQ_ARTIST_RESULTS));
        values.put(MusicAgentRoute.MUSIC_DISCOVERY, Set.of(AgentActionType.SHOW_MUSIC_RESULTS));
        values.put(MusicAgentRoute.ARTIST_LOOKUP, Set.of(AgentActionType.SHOW_QQ_ARTIST_RESULTS));
        values.put(MusicAgentRoute.PLAYLIST_SEARCH, Set.of(AgentActionType.SHOW_QQ_PLAYLIST_RESULTS));
        values.put(MusicAgentRoute.QQ_TREND_DISCOVERY, Set.of(AgentActionType.SHOW_QQ_CHART_RESULTS));
        values.put(MusicAgentRoute.RESULT_PLAYBACK, Set.of(AgentActionType.PLAY_TRACK));
        values.put(MusicAgentRoute.QUEUE_CONTROL, Set.of(AgentActionType.QUEUE_MUSIC_RESULTS));
        return Map.copyOf(values);
    }

    public record PreparedShadow(MusicAgentRoute route, UserGoalGraph graph,
                                 CompiledPlan plan, String error) {
        public PreparedShadow {
            error = error == null ? "" : error.strip();
        }
    }
}
