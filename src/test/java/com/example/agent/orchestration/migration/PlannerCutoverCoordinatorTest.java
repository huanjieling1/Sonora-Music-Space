package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.goal.MusicGoalDecomposer;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import com.example.agent.config.PlannerOperationsProperties;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.orchestration.observability.PlannerRolloutBlockedException;
import com.example.agent.orchestration.observability.PlannerRolloutPolicy;
import com.example.agent.tools.AgentActionContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PlannerCutoverCoordinatorTest {
    private final LegacyRouteGoalGraphAdapter eligibility = mock(LegacyRouteGoalGraphAdapter.class);
    private final MusicGoalDecomposer decomposer = mock(MusicGoalDecomposer.class);
    private final MigratedMusicWorkflowService workflows = mock(MigratedMusicWorkflowService.class);
    private final MigratedMusicWorkflowResponseAdapter responses = mock(MigratedMusicWorkflowResponseAdapter.class);
    private final AgentActionContext actions = mock(AgentActionContext.class);
    private final PlannerOperationsProperties properties = new PlannerOperationsProperties();
    private final PlannerCutoverCoordinator coordinator = new PlannerCutoverCoordinator(eligibility, decomposer,
            workflows, responses, actions, properties);

    @Test
    void shadowAndReadOnlyMutationLeaveLegacyAsTheOnlyOwner() {
        MusicAgentTurn turn = turn("推荐歌曲");
        MusicGoalUnderstanding understanding = understanding();
        UserGoalGraph graph = mock(UserGoalGraph.class);
        when(eligibility.migrated(MusicAgentRoute.MUSIC_DISCOVERY)).thenReturn(true);
        when(decomposer.decompose(turn.request())).thenReturn(graph);
        PreparedMigratedMusicWorkflow shadow = mock(PreparedMigratedMusicWorkflow.class);
        when(shadow.rollout()).thenReturn(new PlannerRolloutPolicy.Decision(
                PlannerRolloutPolicy.Action.SHADOW_ONLY, "SHADOW_MODE"));
        PreparedMigratedMusicWorkflow mutation = mock(PreparedMigratedMusicWorkflow.class);
        when(mutation.rollout()).thenReturn(new PlannerRolloutPolicy.Decision(
                PlannerRolloutPolicy.Action.LEGACY_FALLBACK, "SIDE_EFFECT_NOT_ROLLED_OUT"));
        when(workflows.prepare(turn, graph, MusicTurnPlan.none())).thenReturn(shadow, mutation);

        assertThat(coordinator.executeIfEnabled(turn, understanding)).isEmpty();
        assertThat(coordinator.executeIfEnabled(turn, understanding)).isEmpty();

        verify(workflows, never()).execute(any(), anyMap());
    }

    @Test
    void executeDecisionRunsDynamicExactlyOnceAndPublishesTrustedActions() {
        MusicAgentTurn turn = turn("推荐歌曲");
        MusicGoalUnderstanding understanding = understanding();
        UserGoalGraph graph = mock(UserGoalGraph.class);
        PreparedMigratedMusicWorkflow prepared = mock(PreparedMigratedMusicWorkflow.class);
        when(prepared.rollout()).thenReturn(new PlannerRolloutPolicy.Decision(
                PlannerRolloutPolicy.Action.EXECUTE, "READ_ONLY"));
        MigratedMusicWorkflowResult result = mock(MigratedMusicWorkflowResult.class);
        AgentActionBo action = mock(AgentActionBo.class);
        MusicAgentWorkflowState state = mock(MusicAgentWorkflowState.class);
        when(eligibility.migrated(MusicAgentRoute.MUSIC_DISCOVERY)).thenReturn(true);
        when(decomposer.decompose(turn.request())).thenReturn(graph);
        when(workflows.prepare(turn, graph, MusicTurnPlan.none())).thenReturn(prepared);
        when(workflows.execute(prepared, Map.of())).thenReturn(result);
        when(result.actions()).thenReturn(List.of(action));
        when(responses.adapt(turn, understanding, result)).thenReturn(state);

        assertThat(coordinator.executeIfEnabled(turn, understanding)).contains(state);

        verify(workflows, times(1)).execute(prepared, Map.of());
        verify(actions).add(action);
    }

    @Test
    void runtimeFailureNeverFallsBackAfterDynamicExecutionStarts() {
        MusicAgentTurn turn = turn("推荐歌曲");
        MusicGoalUnderstanding understanding = understanding();
        UserGoalGraph graph = mock(UserGoalGraph.class);
        PreparedMigratedMusicWorkflow prepared = mock(PreparedMigratedMusicWorkflow.class);
        when(prepared.rollout()).thenReturn(new PlannerRolloutPolicy.Decision(
                PlannerRolloutPolicy.Action.EXECUTE, "READ_ONLY"));
        when(eligibility.migrated(MusicAgentRoute.MUSIC_DISCOVERY)).thenReturn(true);
        when(decomposer.decompose(turn.request())).thenReturn(graph);
        when(workflows.prepare(turn, graph, MusicTurnPlan.none())).thenReturn(prepared);
        when(workflows.execute(prepared, Map.of())).thenThrow(new IllegalStateException("provider failed"));

        assertThatThrownBy(() -> coordinator.executeIfEnabled(turn, understanding))
                .isInstanceOf(IllegalStateException.class).hasMessage("provider failed");
        verify(workflows, times(1)).execute(prepared, Map.of());
    }

    @Test
    void preparationFailureMayFallbackButBlockedDecisionDoesNot() {
        MusicAgentTurn turn = turn("推荐歌曲");
        MusicGoalUnderstanding understanding = understanding();
        UserGoalGraph graph = mock(UserGoalGraph.class);
        when(eligibility.migrated(MusicAgentRoute.MUSIC_DISCOVERY)).thenReturn(true);
        when(decomposer.decompose(turn.request())).thenReturn(graph);
        when(workflows.prepare(turn, graph, MusicTurnPlan.none()))
                .thenThrow(new IllegalArgumentException("bad plan"));
        assertThat(coordinator.executeIfEnabled(turn, understanding)).isEmpty();

        PreparedMigratedMusicWorkflow blocked = mock(PreparedMigratedMusicWorkflow.class);
        when(blocked.rollout()).thenReturn(new PlannerRolloutPolicy.Decision(
                PlannerRolloutPolicy.Action.BLOCKED, "KILL_SWITCH"));
        doReturn(blocked).when(workflows).prepare(turn, graph, MusicTurnPlan.none());
        assertThatThrownBy(() -> coordinator.executeIfEnabled(turn, understanding))
                .isInstanceOf(PlannerRolloutBlockedException.class);
    }

    @Test
    void pendingWorkflowResumeRunsBeforeNewPlanningAndPublishesOnlyCurrentActions() {
        MusicAgentTurn reply = turn("确认");
        MigratedMusicWorkflowResult result = mock(MigratedMusicWorkflowResult.class);
        MusicAgentWorkflowState state = mock(MusicAgentWorkflowState.class);
        when(workflows.resumeWaiting(reply)).thenReturn(Optional.of(result));
        when(result.actions()).thenReturn(List.of());
        when(responses.adapt(reply, null, result)).thenReturn(state);

        assertThat(coordinator.resumeIfWaiting(reply)).contains(state);
        verifyNoInteractions(decomposer);
    }

    private static MusicGoalUnderstanding understanding() {
        MusicGoalUnderstanding value = mock(MusicGoalUnderstanding.class);
        when(value.route()).thenReturn(MusicAgentRoute.MUSIC_DISCOVERY);
        when(value.followUpPlan()).thenReturn(MusicTurnPlan.none());
        return value;
    }

    private static MusicAgentTurn turn(String request) {
        return new MusicAgentTurn(7, UUID.randomUUID(), request);
    }
}
