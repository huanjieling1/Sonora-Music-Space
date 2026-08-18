package com.example.agent.service.impl;

import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.ao.ChatAo;
import com.example.agent.model.bo.AgentReplyBo;
import com.example.agent.model.bo.ChatMessageBo;
import com.example.agent.model.bo.ChatResultBo;
import com.example.agent.model.bo.ConversationBo;
import com.example.agent.model.entity.AgentChatMessage;
import com.example.agent.model.entity.AgentConversation;
import com.example.agent.service.AgentChatService;
import com.example.agent.service.ConversationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationServiceImpl implements ConversationService {
    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);
    private static final TypeReference<List<AgentActionBo>> ACTION_LIST_TYPE = new TypeReference<>() { };

    private final ConversationStore store;
    private final AgentChatService agentChatService;
    private final ObjectMapper objectMapper;

    public ConversationServiceImpl(ConversationStore store, AgentChatService agentChatService,
                                   ObjectMapper objectMapper) {
        this.store = store;
        this.agentChatService = agentChatService;
        this.objectMapper = objectMapper;
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
        return store.history(userId, conversationId).stream().map(this::toBo).toList();
    }

    @Override
    public void delete(Long userId, UUID conversationId) {
        store.delete(userId, conversationId);
    }

    @Override
    public ChatResultBo chat(ChatAo request) {
        store.requireOwned(request.userId(), request.conversationId());
        AgentReplyBo reply = agentChatService.chat(request.userId(), request.conversationId(), request.message());
        AgentConversation conversation = store.saveExchange(
                request.userId(), request.conversationId(), request.message(), reply.answer(),
                serializeDisplayActions(reply.actions()));
        return new ChatResultBo(conversation.getConversationId(), reply.answer(), reply.actions());
    }

    private static ConversationBo toBo(AgentConversation conversation) {
        return new ConversationBo(conversation.getConversationId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }

    private ChatMessageBo toBo(AgentChatMessage message) {
        return new ChatMessageBo(
                message.getId(),
                message.getRole(),
                message.getContent(),
                deserializeActions(message),
                message.getCreatedAt());
    }

    private String serializeDisplayActions(List<AgentActionBo> actions) {
        if (actions == null || actions.isEmpty()) return null;
        List<AgentActionBo> displayActions = actions.stream()
                .filter(action -> action.type() == AgentActionType.SHOW_MUSIC_RESULTS
                        || action.type() == AgentActionType.SHOW_QQ_PLAYLIST_RESULTS
                        || action.type() == AgentActionType.SHOW_QQ_ARTIST_RESULTS
                        || action.type() == AgentActionType.SHOW_QQ_CHART_RESULTS
                        || action.type() == AgentActionType.SHOW_MUSIC_PROFILE_STORY
                        || action.type() == AgentActionType.SHOW_PROACTIVE_SUGGESTIONS
                        || action.type() == AgentActionType.SHOW_WORKFLOW_PROGRESS)
                .toList();
        if (displayActions.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(displayActions);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法持久化音乐结果卡片", exception);
        }
    }

    private List<AgentActionBo> deserializeActions(AgentChatMessage message) {
        if (message.getActionsJson() == null || message.getActionsJson().isBlank()) return List.of();
        try {
            return objectMapper.readValue(message.getActionsJson(), ACTION_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            log.warn("历史音乐结果卡片数据损坏，已降级为纯文字消息，messageId={}", message.getId());
            return List.of();
        }
    }
}
