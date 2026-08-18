package com.example.agent.agent.execution;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.UserTasteContext;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public final class DiscoveryExecutionStrategy implements MusicExecutionStrategy {
    private final MusicToolExecutor executor;

    public DiscoveryExecutionStrategy(MusicToolExecutor executor) { this.executor = executor; }
    @Override public String id() { return "music-discovery"; }
    @Override public Set<MusicAgentRoute> routes() { return Set.of(MusicAgentRoute.MUSIC_DISCOVERY); }
    @Override public MusicExecutionResult execute(MusicAgentTurn turn, MusicAgentRoute route,
                                                   UserTasteContext tasteContext) {
        return executor.discover(turn, tasteContext);
    }
}
