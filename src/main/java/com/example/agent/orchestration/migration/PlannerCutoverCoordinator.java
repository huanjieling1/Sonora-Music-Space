package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.goal.MusicGoalDecomposer;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import com.example.agent.config.PlannerOperationsProperties;
import com.example.agent.orchestration.observability.PlannerRolloutBlockedException;
import com.example.agent.orchestration.observability.PlannerRolloutPolicy;
import com.example.agent.tools.AgentActionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** The only production cutover boundary between legacy routes and the generic planner. */
@Component
public final class PlannerCutoverCoordinator {
    private static final Logger log = LoggerFactory.getLogger(PlannerCutoverCoordinator.class);

    private final LegacyRouteGoalGraphAdapter migrationEligibility;
    private final MusicGoalDecomposer decomposer;
    private final MigratedMusicWorkflowService workflows;
    private final MigratedMusicWorkflowResponseAdapter responses;
    private final AgentActionContext actionContext;
    private final PlannerOperationsProperties properties;

    public PlannerCutoverCoordinator(LegacyRouteGoalGraphAdapter migrationEligibility,
                                     MusicGoalDecomposer decomposer,
                                     MigratedMusicWorkflowService workflows,
                                     MigratedMusicWorkflowResponseAdapter responses,
                                     AgentActionContext actionContext,
                                     PlannerOperationsProperties properties) {
        this.migrationEligibility = migrationEligibility;
        this.decomposer = decomposer;
        this.workflows = workflows;
        this.responses = responses;
        this.actionContext = actionContext;
        this.properties = properties;
    }

    /** Resume is checked before intent classification so replies such as “确认” cannot be misrouted. */
    public Optional<MusicAgentWorkflowState> resumeIfWaiting(MusicAgentTurn turn) {
        Optional<MigratedMusicWorkflowResult> resumed = workflows.resumeWaiting(turn);
        return resumed.map(result -> publishAndAdapt(turn, null, result));
    }

    /** Empty means legacy owns the request; a returned state means the generic runtime owned it exclusively. */
    public Optional<MusicAgentWorkflowState> executeIfEnabled(MusicAgentTurn turn,
                                                              MusicGoalUnderstanding understanding) {
        if (!migrationEligibility.migrated(understanding.route())) return Optional.empty();
        final PreparedMigratedMusicWorkflow prepared;
        try {
            prepared = workflows.prepare(turn, decomposer.decompose(turn.request()),
                    understanding.followUpPlan());
        } catch (RuntimeException planningFailure) {
            if (!properties.isFallbackToLegacy()) throw planningFailure;
            log.warn("Generic planner preparation failed; legacy remains owner route={} reason={}",
                    understanding.route(), planningFailure.getMessage());
            return Optional.empty();
        }
        return switch (prepared.rollout().action()) {
            case EXECUTE -> Optional.of(publishAndAdapt(turn, understanding,
                    workflows.execute(prepared, Map.of())));
            case SHADOW_ONLY, LEGACY_FALLBACK -> Optional.empty();
            case BLOCKED -> throw new PlannerRolloutBlockedException(prepared.rollout());
        };
    }

    private MusicAgentWorkflowState publishAndAdapt(MusicAgentTurn turn, MusicGoalUnderstanding understanding,
                                                    MigratedMusicWorkflowResult result) {
        result.actions().forEach(actionContext::add);
        return responses.adapt(turn, understanding, result);
    }
}
