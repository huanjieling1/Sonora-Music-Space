package com.example.agent.service;

import com.example.agent.model.bo.AgentReplyBo;

import java.util.UUID;

public interface AgentChatService {
    AgentReplyBo chat(Long userId, UUID conversationId, String message);
}
