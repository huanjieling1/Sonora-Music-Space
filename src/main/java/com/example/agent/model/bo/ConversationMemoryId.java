package com.example.agent.model.bo;

import java.util.Objects;
import java.util.UUID;

/**
 * LangChain4j 对话记忆键，同时包含用户和逻辑会话，避免跨用户或跨会话共享上下文。
 */
public record ConversationMemoryId(Long userId, UUID conversationId) {
    public ConversationMemoryId {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户标识必须为正数");
        }
        Objects.requireNonNull(conversationId, "会话标识不能为空");
    }

    public String value() {
        return "user:" + userId + ":conversation:" + conversationId;
    }
}
