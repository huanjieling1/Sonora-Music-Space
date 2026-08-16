package com.example.agent.model.vo.agent;

import com.example.agent.model.bo.ConversationBo;

import java.time.LocalDateTime;
import java.util.UUID;

/** 返回给前端侧栏的会话视图对象。 */
public record ConversationVo(UUID id, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ConversationVo from(ConversationBo conversation) {
        return new ConversationVo(conversation.id(), conversation.title(),
                conversation.createdAt(), conversation.updatedAt());
    }
}
