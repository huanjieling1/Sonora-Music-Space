package com.example.agent.agent.contract;

import com.example.agent.model.bo.ConversationMemoryId;

import java.util.UUID;

public record MusicAgentTurn(long userId, UUID conversationId, String request, boolean refreshBatch,
                             String executionDirective) {
    public MusicAgentTurn(long userId, UUID conversationId, String request) {
        this(userId, conversationId, request, false, "");
    }

    public MusicAgentTurn(long userId, UUID conversationId, String request, boolean refreshBatch) {
        this(userId, conversationId, request, refreshBatch, "");
    }

    public MusicAgentTurn {
        if (userId <= 0) throw new IllegalArgumentException("用户标识必须为正数");
        if (conversationId == null) throw new IllegalArgumentException("会话标识不能为空");
        request = request == null ? "" : request.strip();
        if (request.isEmpty()) throw new IllegalArgumentException("用户请求不能为空");
        executionDirective = executionDirective == null ? "" : executionDirective.strip();
    }

    public ConversationMemoryId memoryId() {
        return new ConversationMemoryId(userId, conversationId);
    }
}
