package com.example.agent.model.vo.agent;

import com.example.agent.model.bo.ChatResultBo;

import java.util.List;
import java.util.UUID;

/** 返回给前端的 Agent 回复视图对象。 */
public record ChatVo(UUID conversationId, String answer, List<AgentActionVo> actions) {
    public static ChatVo from(ChatResultBo result) {
        return new ChatVo(result.conversationId(), result.answer(),
                result.actions().stream().map(AgentActionVo::from).toList());
    }
}
