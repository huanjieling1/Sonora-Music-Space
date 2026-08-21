package com.example.agent.orchestration.dag;

import java.util.Optional;
import java.util.UUID;

public interface DagExecutionPersistence {
    void save(DagExecutionSnapshot snapshot);
    Optional<DagExecutionSnapshot> load(UUID workflowId, String principalId);

    default Optional<DagExecutionSnapshot> findLatestWaiting(String principalId, String conversationId) {
        return Optional.empty();
    }

    default void saveResumeContext(UUID workflowId, String principalId, String contextJson) {
    }

    default Optional<String> loadResumeContext(UUID workflowId, String principalId) {
        return Optional.empty();
    }
}
