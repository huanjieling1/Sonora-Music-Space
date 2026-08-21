package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicAgentTurn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicWorkflowHandlerRegistryTest {
    @Test
    void builtInHandlersOwnEveryRouteExactlyOnce() {
        MusicWorkflowHandlerRegistry registry = MusicWorkflowHandlerRegistry.builtIns();

        for (MusicAgentRoute route : MusicAgentRoute.values()) {
            assertThat(registry.require(route).routes()).contains(route);
            assertThat(registry.policy(route)).isNotNull();
        }
    }

    @Test
    void personalizedArtistProfileIsAProfileToEntityToCatalogDag() {
        var route = MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE;
        var handler = MusicWorkflowHandlerRegistry.builtIns().require(route);
        var plan = handler.plan(new MusicWorkflowPlanningContext(
                new MusicAgentTurn(1, UUID.randomUUID(),
                        "把你认为的我最喜欢的歌手的个人资料找出来"), route, true));

        assertThat(plan.tasks()).extracting(task -> task.id())
                .containsExactly("intent", "profile", "resolution", "execution", "verification", "response");
        assertThat(plan.tasks().stream().filter(task -> task.id().equals("execution")).findFirst().orElseThrow()
                .dependencies()).containsExactly("resolution");
    }

    @Test
    void duplicateRouteOwnershipFailsFast() {
        MusicWorkflowHandler all = handler("all", Set.of(MusicAgentRoute.values()));
        MusicWorkflowHandler duplicate = handler("duplicate", Set.of(MusicAgentRoute.MUSIC_DISCOVERY));

        assertThatThrownBy(() -> new MusicWorkflowHandlerRegistry(List.of(all, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MUSIC_DISCOVERY", "重复注册");
    }

    @Test
    void missingRouteOwnershipFailsFast() {
        assertThatThrownBy(() -> new MusicWorkflowHandlerRegistry(List.of(
                handler("partial", Set.of(MusicAgentRoute.CONVERSATION)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注册");
    }

    private static MusicWorkflowHandler handler(String id, Set<MusicAgentRoute> routes) {
        return new MusicWorkflowHandler() {
            @Override public String id() { return id; }
            @Override public Set<MusicAgentRoute> routes() { return routes; }
            @Override public MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
                throw new UnsupportedOperationException();
            }
            @Override public MusicWorkflowPolicy policy(MusicAgentRoute route) {
                return MusicWorkflowPolicy.readOnly(1, false, Set.of());
            }
        };
    }
}
