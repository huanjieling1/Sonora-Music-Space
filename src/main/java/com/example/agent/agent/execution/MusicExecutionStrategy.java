package com.example.agent.agent.execution;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.UserTasteContext;

import java.util.Set;

/** Command strategy for an executable route. */
public interface MusicExecutionStrategy {
    String id();

    Set<MusicAgentRoute> routes();

    MusicExecutionResult execute(MusicAgentTurn turn, MusicAgentRoute route, UserTasteContext tasteContext);
}
