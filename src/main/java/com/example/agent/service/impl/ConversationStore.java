package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.entity.AgentChatMessage;
import com.example.agent.model.entity.AgentConversation;
import com.example.agent.repository.AgentChatMessageRepository;
import com.example.agent.repository.AgentConversationRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationStore {
    private final AgentConversationRepository conversations;
    private final AgentChatMessageRepository messages;

    public ConversationStore(AgentConversationRepository conversations, AgentChatMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public AgentConversation create(Long userId) {
        return conversations.saveAndFlush(AgentConversation.create(userId));
    }

    @Transactional(readOnly = true)
    public List<AgentConversation> list(Long userId) {
        return conversations.findAllByUserIdAndDeletedFalseOrderByUpdatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public AgentConversation requireOwned(Long userId, UUID conversationId) {
        return conversations.findByIdAndUserIdAndDeletedFalse(conversationId.toString(), userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "会话不存在或无权访问"));
    }

    @Transactional(readOnly = true)
    public List<AgentChatMessage> history(Long userId, UUID conversationId) {
        AgentConversation conversation = requireOwned(userId, conversationId);
        return messages.findAllByConversationIdOrderByIdAsc(conversation.getId());
    }

    @Transactional
    public void delete(Long userId, UUID conversationId) {
        AgentConversation conversation = requireOwned(userId, conversationId);
        conversation.markDeleted(LocalDateTime.now());
        conversations.save(conversation);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> loadMemory(ConversationMemoryId memoryId, int maxMessages) {
        AgentConversation conversation = requireOwned(memoryId.userId(), memoryId.conversationId());
        List<AgentChatMessage> latest = messages.findByConversationIdOrderByIdDesc(
                conversation.getId(), PageRequest.of(0, maxMessages));
        List<AgentChatMessage> chronological = new ArrayList<>(latest);
        Collections.reverse(chronological);
        return chronological.stream().map(message -> switch (message.getRole()) {
            case USER -> (ChatMessage) UserMessage.from(message.getContent());
            case ASSISTANT -> AiMessage.from(message.getContent());
        }).toList();
    }

    @Transactional
    public AgentConversation saveExchange(Long userId, UUID conversationId, String userMessage,
                                          String answer, String actionsJson) {
        AgentConversation conversation = requireOwned(userId, conversationId);
        LocalDateTime now = LocalDateTime.now();
        messages.save(AgentChatMessage.user(conversation.getId(), userMessage, now));
        messages.save(AgentChatMessage.assistant(conversation.getId(), answer, actionsJson, now));
        conversation.recordExchange(userMessage, now);
        return conversations.save(conversation);
    }

    public AgentConversation saveExchange(Long userId, UUID conversationId, String userMessage, String answer) {
        return saveExchange(userId, conversationId, userMessage, answer, null);
    }
}
