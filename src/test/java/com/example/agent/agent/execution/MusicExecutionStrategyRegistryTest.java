package com.example.agent.agent.execution;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.UserTasteContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicExecutionStrategyRegistryTest {
    private static final Set<MusicAgentRoute> EXECUTABLE = Set.of(
            MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST, MusicAgentRoute.PLAYLIST_SEARCH,
            MusicAgentRoute.ARTIST_LOOKUP, MusicAgentRoute.QQ_TREND_DISCOVERY,
            MusicAgentRoute.MUSIC_DISCOVERY, MusicAgentRoute.RESULT_PLAYBACK,
            MusicAgentRoute.RESULT_NAVIGATION, MusicAgentRoute.QUEUE_CONTROL);

    @Test
    void missingDuplicateAndNonExecutableRegistrationsFailFast() {
        assertThatThrownBy(() -> new MusicExecutionStrategyRegistry(List.of(
                strategy("partial", Set.of(MusicAgentRoute.MUSIC_DISCOVERY)))))
                .hasMessageContaining("未注册");

        MusicExecutionStrategy all = strategy("all", EXECUTABLE);
        assertThatThrownBy(() -> new MusicExecutionStrategyRegistry(List.of(all,
                strategy("duplicate", Set.of(MusicAgentRoute.MUSIC_DISCOVERY)))))
                .hasMessageContaining("MUSIC_DISCOVERY", "重复注册");

        assertThatThrownBy(() -> new MusicExecutionStrategyRegistry(List.of(
                strategy("invalid", Set.of(MusicAgentRoute.CONVERSATION)))))
                .hasMessageContaining("非执行路由");
    }

    private static MusicExecutionStrategy strategy(String id, Set<MusicAgentRoute> routes) {
        return new MusicExecutionStrategy() {
            @Override public String id() { return id; }
            @Override public Set<MusicAgentRoute> routes() { return routes; }
            @Override public MusicExecutionResult execute(MusicAgentTurn turn, MusicAgentRoute route,
                                                           UserTasteContext tasteContext) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
