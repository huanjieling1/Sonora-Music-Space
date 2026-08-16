package com.example.agent.service.impl;

import com.example.agent.model.ao.ChatAo;
import com.example.agent.model.bo.AgentReplyBo;
import com.example.agent.model.bo.ChatMessageBo;
import com.example.agent.model.bo.ChatResultBo;
import com.example.agent.model.bo.ConversationBo;
import com.example.agent.model.entity.AgentChatMessage;
import com.example.agent.model.entity.AgentConversation;
import com.example.agent.service.AgentChatService;
import com.example.agent.service.ConversationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationServiceImpl implements ConversationService {
    private final ConversationStore store;
    private final AgentChatService agentChatService;

    public ConversationServiceImpl(ConversationStore store, AgentChatService agentChatService) {
        this.store = store;
        this.agentChatService = agentChatService;
    }

    @Override
    public ConversationBo create(Long userId) {
        return toBo(store.create(userId));
    }

    @Override
    public List<ConversationBo> list(Long userId) {
        return store.list(userId).stream().map(ConversationServiceImpl::toBo).toList();
    }

    @Override
    public List<ChatMessageBo> history(Long userId, UUID conversationId) {
        return store.history(userId, conversationId).stream().map(ConversationServiceImpl::toBo).toList();
    }

    @Override
    public ChatResultBo chat(ChatAo request) {
        store.requireOwned(request.userId(), request.conversationId());
        AgentReplyBo reply = agentChatService.chat(request.userId(), request.conversationId(), request.message());
        AgentConversation conversation = store.saveExchange(
                request.userId(), request.conversationId(), request.message(), reply.answer());
        return new ChatResultBo(conversation.getConversationId(), reply.answer(), reply.actions());
    }

    private static ConversationBo toBo(AgentConversation conversation) {
        return new ConversationBo(conversation.getConversationId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private static ChatMessageBo toBo(AgentChatMessage message) {
        return new ChatMessageBo(message.getId(), message.getRole(), message.getContent(), message.getCreatedAt());
    }
}
