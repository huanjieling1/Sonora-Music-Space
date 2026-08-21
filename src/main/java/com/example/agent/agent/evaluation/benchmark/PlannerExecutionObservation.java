package com.example.agent.agent.evaluation.benchmark;

/** Ground-truth comparison used for completion and false-success metrics. */
public record PlannerExecutionObservation(
        String id,
        boolean actuallySatisfied,
        boolean reportedCompleted
) {
    public PlannerExecutionObservation {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("执行观测 ID 不能为空");
    }
}
