package com.example.agent.orchestration.migration;

import com.example.agent.model.bo.AgentActionBo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MigratedMusicActionRegistryTest {
    @Test
    void publishesOnlyActionsFromAcceptedTasksAndNeverPublishesThemTwice() {
        MigratedMusicActionRegistry registry = new MigratedMusicActionRegistry();
        UUID workflowId = UUID.randomUUID();
        AgentActionBo accepted = mock(AgentActionBo.class);
        AgentActionBo rejected = mock(AgentActionBo.class);
        when(accepted.id()).thenReturn(UUID.randomUUID());
        when(rejected.id()).thenReturn(UUID.randomUUID());
        registry.record(workflowId, "accepted-task", List.of(accepted));
        registry.record(workflowId, "rejected-task", List.of(rejected));

        assertThat(registry.drainAccepted(workflowId, Set.of("accepted-task"))).containsExactly(accepted);
        assertThat(registry.drainAccepted(workflowId, Set.of("accepted-task"))).isEmpty();
    }
}
