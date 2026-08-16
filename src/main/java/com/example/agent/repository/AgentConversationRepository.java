package com.example.agent.repository;

import com.example.agent.model.entity.AgentConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, String> {
    List<AgentConversation> findAllByUserIdAndDeletedFalseOrderByUpdatedAtDesc(Long userId);
    Optional<AgentConversation> findByIdAndUserIdAndDeletedFalse(String id, Long userId);
}
