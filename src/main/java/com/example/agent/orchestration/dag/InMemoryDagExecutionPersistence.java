package com.example.agent.orchestration.dag;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test/local implementation with the same user isolation semantics as JPA persistence. */
public final class InMemoryDagExecutionPersistence implements DagExecutionPersistence {
    private final ConcurrentHashMap<UUID, DagExecutionSnapshot> values = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> resumeContexts = new ConcurrentHashMap<>();

    @Override
    public void save(DagExecutionSnapshot snapshot) {
        DagExecutionSnapshot previous = values.putIfAbsent(snapshot.workflowId(), snapshot);
        if (previous != null) {
            if (!previous.principalId().equals(snapshot.principalId())) {
                throw new SecurityException("禁止覆盖其他用户的工作流");
            }
            values.put(snapshot.workflowId(), snapshot);
        }
    }

    @Override
    public Optional<DagExecutionSnapshot> load(UUID workflowId, String principalId) {
        DagExecutionSnapshot value = values.get(workflowId);
        return value != null && value.principalId().equals(principalId)
                ? Optional.of(value) : Optional.empty();
    }

    @Override
    public Optional<DagExecutionSnapshot> findLatestWaiting(String principalId, String conversationId) {
        return values.values().stream()
                .filter(value -> value.principalId().equals(principalId))
                .filter(value -> value.conversationId().equals(conversationId))
                .filter(value -> value.status() == DagWorkflowStatus.WAITING_USER)
                .max(java.util.Comparator.comparing(DagExecutionSnapshot::updatedAt));
    }

    @Override
    public void saveResumeContext(UUID workflowId, String principalId, String contextJson) {
        DagExecutionSnapshot value = values.get(workflowId);
        if (value == null || !value.principalId().equals(principalId)) {
            throw new SecurityException("禁止写入其他用户的工作流上下文");
        }
        resumeContexts.put(workflowId, contextJson);
    }

    @Override
    public Optional<String> loadResumeContext(UUID workflowId, String principalId) {
        return load(workflowId, principalId).flatMap(ignored -> Optional.ofNullable(resumeContexts.get(workflowId)));
    }
}
