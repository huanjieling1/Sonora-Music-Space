package com.example.agent.orchestration.dag;

import com.example.agent.model.entity.GenericWorkflowExecution;
import com.example.agent.repository.GenericWorkflowExecutionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** MySQL-backed snapshot persistence used to restore progress after page or process refresh. */
@Component
public class JpaDagExecutionPersistence implements DagExecutionPersistence {
    private final GenericWorkflowExecutionRepository repository;
    private final ObjectMapper objectMapper;

    public JpaDagExecutionPersistence(GenericWorkflowExecutionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(DagExecutionSnapshot snapshot) {
        String workflowId = snapshot.workflowId().toString();
        String planJson = write(snapshot.plan());
        String stateJson = write(snapshot);
        GenericWorkflowExecution entity = repository.findById(workflowId).orElse(null);
        if (entity == null) {
            repository.save(GenericWorkflowExecution.create(workflowId, snapshot.principalId(),
                    snapshot.conversationId(), planJson, stateJson, snapshot.status().name()));
            return;
        }
        if (!entity.getPrincipalId().equals(snapshot.principalId())) {
            throw new SecurityException("禁止覆盖其他用户的工作流");
        }
        entity.update(planJson, stateJson, snapshot.status().name());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DagExecutionSnapshot> load(UUID workflowId, String principalId) {
        if (workflowId == null || principalId == null || principalId.isBlank()) return Optional.empty();
        return repository.findByWorkflowIdAndPrincipalId(workflowId.toString(), principalId.strip())
                .map(value -> read(value.getStateJson()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DagExecutionSnapshot> findLatestWaiting(String principalId, String conversationId) {
        if (principalId == null || principalId.isBlank() || conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }
        return repository.findFirstByPrincipalIdAndConversationIdAndStatusOrderByUpdatedAtDesc(
                        principalId.strip(), conversationId.strip(), DagWorkflowStatus.WAITING_USER.name())
                .map(value -> read(value.getStateJson()));
    }

    @Override
    @Transactional
    public void saveResumeContext(UUID workflowId, String principalId, String contextJson) {
        GenericWorkflowExecution entity = repository.findByWorkflowIdAndPrincipalId(
                        workflowId.toString(), principalId.strip())
                .orElseThrow(() -> new SecurityException("禁止写入其他用户的工作流上下文"));
        entity.updateResumeContext(contextJson);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> loadResumeContext(UUID workflowId, String principalId) {
        if (workflowId == null || principalId == null || principalId.isBlank()) return Optional.empty();
        return repository.findByWorkflowIdAndPrincipalId(workflowId.toString(), principalId.strip())
                .map(GenericWorkflowExecution::getResumeContextJson)
                .filter(value -> value != null && !value.isBlank());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法持久化 DAG 工作流", error);
        }
    }

    private DagExecutionSnapshot read(String json) {
        try {
            return objectMapper.readValue(json, DagExecutionSnapshot.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法恢复 DAG 工作流", error);
        }
    }
}
