package com.example.agent.agent.evaluation;

import java.util.List;

/** Goal-level coverage and constraint evaluation. */
public record GoalEvaluation(
        String goalId,
        EvaluationDecision decision,
        List<String> taskIds,
        List<EvaluationFinding> findings
) {
    public GoalEvaluation {
        if (goalId == null || goalId.isBlank()) throw new IllegalArgumentException("目标验收必须关联目标");
        goalId = goalId.strip();
        decision = decision == null ? EvaluationDecision.FAIL : decision;
        taskIds = taskIds == null ? List.of() : List.copyOf(taskIds);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
