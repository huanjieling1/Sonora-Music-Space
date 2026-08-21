package com.example.agent.agent.planner;

import java.util.List;

/** Immutable validation report, including worst-case resource budgets. */
public record PlanValidationResult(
        List<PlanValidationIssue> issues,
        int estimatedCostUnits,
        int worstCaseDurationSeconds,
        int totalAttempts
) {
    public PlanValidationResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (estimatedCostUnits < 0 || worstCaseDurationSeconds < 0 || totalAttempts < 0) {
            throw new IllegalArgumentException("计划预算统计不能为负数");
        }
    }

    public boolean valid() {
        return issues.isEmpty();
    }
}
