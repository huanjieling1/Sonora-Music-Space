package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;

import java.util.Set;

/** Runtime Strategy: owns the route-specific agent collaboration after a plan has been created. */
public interface MusicWorkflowRuntimeHandler {
    String id();

    Set<MusicAgentRoute> routes();

    MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context);
}
