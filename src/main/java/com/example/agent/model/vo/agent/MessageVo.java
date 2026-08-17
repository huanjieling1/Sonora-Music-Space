package com.example.agent.model.vo.agent;

import com.example.agent.model.bo.ChatMessageBo;

import java.time.LocalDateTime;
import java.util.List;

/** 返回给前端的历史消息视图对象。 */
public record MessageVo(
        Long id,
        String role,
        String content,
        List<AgentActionVo> actions,
        LocalDateTime createdAt
) {
    public static MessageVo from(ChatMessageBo message) {
        return new MessageVo(
                message.id(),
                message.role().name(),
                message.content(),
                message.actions().stream().map(AgentActionVo::from).toList(),
                message.createdAt());
    }
}
