package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.MusicWorkflowSnapshot;
import com.example.agent.agent.contract.MusicWorkflowStatus;
import com.example.agent.orchestration.MusicWorkflowSupervisor;
import com.example.agent.orchestration.migration.MusicMigrationShadowService;
import com.example.agent.orchestration.migration.PlannerCutoverCoordinator;
import com.example.agent.orchestration.runtime.MusicWorkflowRuntimeHandlerRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MusicMainAgentCutoverTest {
    private final MusicGoalUnderstandingAgent understandingAgent = mock(MusicGoalUnderstandingAgent.class);
    private final MusicWorkflowRuntimeHandlerRegistry runtimeHandlers = mock(MusicWorkflowRuntimeHandlerRegistry.class);
    private final MusicWorkflowSupervisor supervisor = mock(MusicWorkflowSupervisor.class);
    private final MusicMigrationShadowService shadow = mock(MusicMigrationShadowService.class);
    private final PlannerCutoverCoordinator cutover = mock(PlannerCutoverCoordinator.class);
    private final MusicMainAgent agent = new MusicMainAgent(understandingAgent, runtimeHandlers, supervisor,
            shadow, cutover);

    @Test
    void resumedWorkflowBypassesIntentAndLegacyRuntime() {
        MusicAgentTurn turn = turn("确认");
        MusicAgentWorkflowState state = mock(MusicAgentWorkflowState.class);
        when(cutover.resumeIfWaiting(turn)).thenReturn(Optional.of(state));

        assertThat(agent.run(turn)).isSameAs(state);

        verifyNoInteractions(understandingAgent, runtimeHandlers, supervisor, shadow);
    }

    @Test
    void dynamicOwnershipBypassesLegacyRuntimeAndShadowExecution() {
        MusicAgentTurn turn = turn("推荐三首 Mili 的歌，同时查询她的资料");
        MusicGoalUnderstanding goal = mock(MusicGoalUnderstanding.class);
        MusicAgentWorkflowState state = mock(MusicAgentWorkflowState.class);
        MusicWorkflowSnapshot workflow = new MusicWorkflowSnapshot(UUID.randomUUID(), turn.request(),
                MusicWorkflowStatus.COMPLETED, List.of());
        when(cutover.resumeIfWaiting(turn)).thenReturn(Optional.empty());
        when(understandingAgent.understand(turn)).thenReturn(goal);
        when(goal.route()).thenReturn(MusicAgentRoute.MUSIC_DISCOVERY);
        when(cutover.executeIfEnabled(turn, goal)).thenReturn(Optional.of(state));
        when(state.workflow()).thenReturn(workflow);

        assertThat(agent.run(turn)).isSameAs(state);

        verifyNoInteractions(runtimeHandlers, supervisor, shadow);
    }

    private static MusicAgentTurn turn(String request) {
        return new MusicAgentTurn(9, UUID.randomUUID(), request);
    }
}
