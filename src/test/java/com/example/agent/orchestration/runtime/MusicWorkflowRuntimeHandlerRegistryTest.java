package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicWorkflowRuntimeHandlerRegistryTest {
    @Test
    void completeExclusiveRegistryResolvesEveryRoute() {
        MusicWorkflowRuntimeHandlerRegistry registry = new MusicWorkflowRuntimeHandlerRegistry(List.of(
                handler("all", Set.of(MusicAgentRoute.values()))));

        for (MusicAgentRoute route : MusicAgentRoute.values()) {
            assertThat(registry.require(route).routes()).contains(route);
        }
    }

    @Test
    void duplicateAndMissingRoutesAreRejected() {
        MusicWorkflowRuntimeHandler all = handler("all", Set.of(MusicAgentRoute.values()));
        assertThatThrownBy(() -> new MusicWorkflowRuntimeHandlerRegistry(List.of(all,
                handler("duplicate", Set.of(MusicAgentRoute.PROFILE_ANALYSIS)))))
                .hasMessageContaining("PROFILE_ANALYSIS", "重复注册");

        assertThatThrownBy(() -> new MusicWorkflowRuntimeHandlerRegistry(List.of(
                handler("partial", Set.of(MusicAgentRoute.CONVERSATION)))))
                .hasMessageContaining("未注册");
    }

    private static MusicWorkflowRuntimeHandler handler(String id, Set<MusicAgentRoute> routes) {
        return new MusicWorkflowRuntimeHandler() {
            @Override public String id() { return id; }
            @Override public Set<MusicAgentRoute> routes() { return routes; }
            @Override public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
