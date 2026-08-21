package com.example.agent.orchestration.replanning;

import java.time.Instant;
import java.util.Set;

/** Persisted audit record for one bounded replanning attempt. */
public record ReplanRecord(
        int attempt,
        String failedTaskId,
        String errorCode,
        Set<String> replacedTaskIds,
        Set<String> preservedTaskIds,
        String planFingerprint,
        ReplanResult.Kind outcome,
        String message,
        Instant occurredAt
) {
    public ReplanRecord {
        if (attempt < 1) throw new IllegalArgumentException("重规划审计次数必须大于零");
        failedTaskId = failedTaskId == null ? "" : failedTaskId.strip();
        errorCode = errorCode == null ? "" : errorCode.strip();
        replacedTaskIds = replacedTaskIds == null ? Set.of() : Set.copyOf(replacedTaskIds);
        preservedTaskIds = preservedTaskIds == null ? Set.of() : Set.copyOf(preservedTaskIds);
        planFingerprint = planFingerprint == null ? "" : planFingerprint.strip();
        outcome = outcome == null ? ReplanResult.Kind.FAIL : outcome;
        message = message == null ? "" : message.strip();
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
