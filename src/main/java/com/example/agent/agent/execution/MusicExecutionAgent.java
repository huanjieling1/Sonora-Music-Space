package com.example.agent.agent.execution;

import com.example.agent.agent.capability.AgentToolAuthorizer;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.tools.AgentActionContext;
import com.example.agent.tools.MusicAgentTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** Strict execution role; route commands are supplied by an extensible strategy registry. */
@Component
public class MusicExecutionAgent {
    private final MusicExecutionStrategyRegistry strategies;

    @Autowired
    public MusicExecutionAgent(MusicExecutionStrategyRegistry strategies) {
        this.strategies = strategies;
    }

    /** Compatibility constructor for focused tests and non-Spring embedding. */
    public MusicExecutionAgent(MusicAgentTools tools, AgentActionContext actionContext,
                               MusicAgentSessionStore sessionStore, AgentToolAuthorizer toolAuthorizer) {
        MusicToolExecutor executor = new MusicToolExecutor(tools, actionContext, sessionStore, toolAuthorizer);
        this.strategies = new MusicExecutionStrategyRegistry(List.of(
                new DiscoveryExecutionStrategy(executor), new QqCatalogExecutionStrategy(executor),
                new PlaybackExecutionStrategy(executor)));
    }

    public MusicExecutionResult execute(MusicAgentTurn turn, MusicAgentRoute route) {
        return execute(turn, route, null);
    }

    public MusicExecutionResult execute(MusicAgentTurn turn, MusicAgentRoute route,
                                        UserTasteContext tasteContext) {
        if (turn == null) throw new IllegalArgumentException("执行输入不能为空");
        if (route == null) throw new IllegalArgumentException("执行路由不能为空");
        return strategies.require(route).execute(turn, route, tasteContext);
    }
}
