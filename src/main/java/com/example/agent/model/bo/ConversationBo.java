package com.example.agent.model.bo;

import java.time.LocalDateTime;
import java.util.UUID;

/** 会话业务对象，不向 Controller 暴露持久化实体。 */
public record ConversationBo(
        UUID id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
