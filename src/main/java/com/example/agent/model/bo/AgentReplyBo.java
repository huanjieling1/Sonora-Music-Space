package com.example.agent.model.bo;

import java.util.List;

/** Result returned by the main Agent before conversation persistence is applied. */
public record AgentReplyBo(String answer, List<AgentActionBo> actions) {
    public AgentReplyBo {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
