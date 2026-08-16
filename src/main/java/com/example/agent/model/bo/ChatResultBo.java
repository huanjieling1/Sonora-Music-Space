package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

/** Agent 完成一次回复后的业务结果。 */
public record ChatResultBo(UUID conversationId, String answer, List<AgentActionBo> actions) {
    public ChatResultBo {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
