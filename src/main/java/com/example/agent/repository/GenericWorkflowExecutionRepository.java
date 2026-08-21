package com.example.agent.repository;

import com.example.agent.model.entity.GenericWorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GenericWorkflowExecutionRepository extends JpaRepository<GenericWorkflowExecution, String> {
    Optional<GenericWorkflowExecution> findByWorkflowIdAndPrincipalId(String workflowId, String principalId);

    Optional<GenericWorkflowExecution> findFirstByPrincipalIdAndConversationIdAndStatusOrderByUpdatedAtDesc(
            String principalId, String conversationId, String status);
}
