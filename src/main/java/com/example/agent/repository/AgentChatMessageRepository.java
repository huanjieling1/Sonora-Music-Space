package com.example.agent.repository;

import com.example.agent.model.entity.AgentChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AgentChatMessageRepository extends JpaRepository<AgentChatMessage, Long> {
    List<AgentChatMessage> findAllByConversationIdOrderByIdAsc(String conversationId);
    List<AgentChatMessage> findByConversationIdOrderByIdDesc(String conversationId, Pageable pageable);
    void deleteAllByConversationId(String conversationId);
}
