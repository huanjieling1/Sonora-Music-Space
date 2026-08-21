package com.example.agent.agent.response;

import com.example.agent.agent.evaluation.GroundedClaim;

import java.util.List;

/** One user-visible statement grounded in a single accepted task result. */
public record GroundedResponseFact(
        String goalId,
        String sourceTaskId,
        Kind kind,
        String statement,
        List<String> evidenceIds,
        List<String> entityIds
) {
    public GroundedResponseFact {
        if (goalId == null || goalId.isBlank()) throw new IllegalArgumentException("响应事实必须关联目标");
        if (sourceTaskId == null || sourceTaskId.isBlank()) throw new IllegalArgumentException("响应事实必须关联任务");
        goalId = goalId.strip();
        sourceTaskId = sourceTaskId.strip();
        kind = kind == null ? Kind.EXTERNAL_FACT : kind;
        if (statement == null || statement.isBlank()) throw new IllegalArgumentException("响应事实文本不能为空");
        statement = statement.strip();
        evidenceIds = evidenceIds == null ? List.of() : evidenceIds.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::strip).distinct().toList();
        entityIds = entityIds == null ? List.of() : entityIds.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::strip).distinct().toList();
    }

    public enum Kind { EXTERNAL_FACT, INFERENCE, STATE_CHANGE }

    public GroundedClaim claim() {
        return new GroundedClaim(statement, evidenceIds);
    }
}
