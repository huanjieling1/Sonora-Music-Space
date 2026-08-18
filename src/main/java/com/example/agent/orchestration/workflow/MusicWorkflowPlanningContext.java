package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;

/** Verified inputs available while a route-specific workflow is being planned. */
public record MusicWorkflowPlanningContext(
        MusicAgentTurn turn,
        MusicAgentRoute route,
        boolean usesProfile
) {
    public MusicWorkflowPlanningContext {
        if (turn == null) throw new IllegalArgumentException("工作流输入不能为空");
        if (route == null) throw new IllegalArgumentException("工作流路由不能为空");
    }
}
