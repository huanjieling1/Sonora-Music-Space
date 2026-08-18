package com.example.agent.agent.execution;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Auto-discovers execution commands and validates exclusive ownership of every executable route. */
@Component
public final class MusicExecutionStrategyRegistry {
    private static final Set<MusicAgentRoute> EXECUTABLE_ROUTES = EnumSet.of(
            MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST, MusicAgentRoute.PLAYLIST_SEARCH,
            MusicAgentRoute.ARTIST_LOOKUP, MusicAgentRoute.QQ_TREND_DISCOVERY,
            MusicAgentRoute.MUSIC_DISCOVERY, MusicAgentRoute.RESULT_PLAYBACK,
            MusicAgentRoute.RESULT_NAVIGATION, MusicAgentRoute.QUEUE_CONTROL);

    private final Map<MusicAgentRoute, MusicExecutionStrategy> strategies;

    public MusicExecutionStrategyRegistry(List<MusicExecutionStrategy> discovered) {
        EnumMap<MusicAgentRoute, MusicExecutionStrategy> values = new EnumMap<>(MusicAgentRoute.class);
        for (MusicExecutionStrategy strategy : discovered == null ? List.<MusicExecutionStrategy>of() : discovered) {
            if (strategy == null || strategy.routes() == null || strategy.routes().isEmpty()) {
                throw new IllegalStateException("执行策略必须声明至少一个路由");
            }
            for (MusicAgentRoute route : strategy.routes()) {
                if (!EXECUTABLE_ROUTES.contains(route)) {
                    throw new IllegalStateException("非执行路由不能注册工具策略：" + route);
                }
                MusicExecutionStrategy previous = values.putIfAbsent(route, strategy);
                if (previous != null) {
                    throw new IllegalStateException("执行路由 " + route + " 被重复注册到 "
                            + previous.id() + " 和 " + strategy.id());
                }
            }
        }
        Set<MusicAgentRoute> missing = EnumSet.copyOf(EXECUTABLE_ROUTES);
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) throw new IllegalStateException("存在未注册的音乐执行路由：" + missing);
        strategies = Map.copyOf(values);
    }

    public MusicExecutionStrategy require(MusicAgentRoute route) {
        MusicExecutionStrategy strategy = strategies.get(route);
        if (strategy == null) throw new IllegalArgumentException("该路由不允许执行音乐工具：" + route);
        return strategy;
    }
}
