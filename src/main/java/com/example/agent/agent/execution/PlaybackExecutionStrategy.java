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
public final class PlaybackExecutionStrategy implements MusicExecutionStrategy {
    private final Map<MusicAgentRoute, Function<MusicAgentTurn, MusicExecutionResult>> commands;

    public PlaybackExecutionStrategy(MusicToolExecutor executor) {
        EnumMap<MusicAgentRoute, Function<MusicAgentTurn, MusicExecutionResult>> values =
                new EnumMap<>(MusicAgentRoute.class);
        values.put(MusicAgentRoute.RESULT_PLAYBACK,
                turn -> executor.playResult(turn, MusicAgentRoute.RESULT_PLAYBACK));
        values.put(MusicAgentRoute.RESULT_NAVIGATION,
                turn -> executor.navigateResults(turn, MusicAgentRoute.RESULT_NAVIGATION));
        values.put(MusicAgentRoute.QUEUE_CONTROL,
                turn -> executor.queueResults(MusicAgentRoute.QUEUE_CONTROL));
        commands = Map.copyOf(values);
    }

    @Override public String id() { return "playback-control"; }
    @Override public Set<MusicAgentRoute> routes() { return commands.keySet(); }

    @Override
    public MusicExecutionResult execute(MusicAgentTurn turn, MusicAgentRoute route, UserTasteContext tasteContext) {
        Function<MusicAgentTurn, MusicExecutionResult> command = commands.get(route);
        if (command == null) throw new IllegalArgumentException("播放策略不支持路由：" + route);
        return command.apply(turn);
    }
}
