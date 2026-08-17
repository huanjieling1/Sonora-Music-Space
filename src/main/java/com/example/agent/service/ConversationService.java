package com.example.agent.service;

import com.example.agent.model.ao.ChatAo;
import com.example.agent.model.bo.ChatMessageBo;
import com.example.agent.model.bo.ChatResultBo;
import com.example.agent.model.bo.ConversationBo;

import java.util.List;
import java.util.UUID;

public interface ConversationService {
    ConversationBo create(Long userId);

    List<ConversationBo> list(Long userId);

    List<ChatMessageBo> history(Long userId, UUID conversationId);

    void delete(Long userId, UUID conversationId);

    ChatResultBo chat(ChatAo request);
}
