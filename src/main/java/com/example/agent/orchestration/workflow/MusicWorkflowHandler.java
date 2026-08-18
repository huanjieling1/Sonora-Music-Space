package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicWorkflowPlan;

import java.util.Set;

/** Strategy extension point for one coherent family of music workflows. */
public interface MusicWorkflowHandler {
    String id();

    Set<MusicAgentRoute> routes();

    MusicWorkflowPlan plan(MusicWorkflowPlanningContext context);

    MusicWorkflowPolicy policy(MusicAgentRoute route);
}
