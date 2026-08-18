package com.example.agent.agent.execution;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.UserTasteContext;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public final class QqCatalogExecutionStrategy implements MusicExecutionStrategy {
    private final Map<MusicAgentRoute, Function<MusicAgentTurn, MusicExecutionResult>> commands;

    public QqCatalogExecutionStrategy(MusicToolExecutor executor) {
        EnumMap<MusicAgentRoute, Function<MusicAgentTurn, MusicExecutionResult>> values =
                new EnumMap<>(MusicAgentRoute.class);
        values.put(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST,
                turn -> executor.randomPlaylist(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST));
        values.put(MusicAgentRoute.PLAYLIST_SEARCH,
                turn -> executor.searchPlaylists(turn, MusicAgentRoute.PLAYLIST_SEARCH));
        values.put(MusicAgentRoute.ARTIST_LOOKUP,
                turn -> executor.lookupArtist(turn, MusicAgentRoute.ARTIST_LOOKUP));
        values.put(MusicAgentRoute.QQ_TREND_DISCOVERY,
                turn -> executor.discoverTrends(turn, MusicAgentRoute.QQ_TREND_DISCOVERY));
        commands = Map.copyOf(values);
    }

    @Override public String id() { return "qq-catalog"; }
    @Override public Set<MusicAgentRoute> routes() { return commands.keySet(); }

    @Override
    public MusicExecutionResult execute(MusicAgentTurn turn, MusicAgentRoute route, UserTasteContext tasteContext) {
        Function<MusicAgentTurn, MusicExecutionResult> command = commands.get(route);
        if (command == null) throw new IllegalArgumentException("QQ 曲库策略不支持路由：" + route);
        return command.apply(turn);
    }
}
