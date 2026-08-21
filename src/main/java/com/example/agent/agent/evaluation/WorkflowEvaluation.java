package com.example.agent.agent.evaluation;

import java.util.List;
import java.util.Map;

/** Final two-layer acceptance result. A workflow is complete only when this result is PASS. */
public record WorkflowEvaluation(
        EvaluationDecision decision,
        Map<String, TaskEvaluation> tasks,
        List<GoalEvaluation> goals,
        List<EvaluationFinding> findings
) {
    public WorkflowEvaluation {
        decision = decision == null ? EvaluationDecision.FAIL : decision;
        tasks = tasks == null ? Map.of() : Map.copyOf(tasks);
        goals = goals == null ? List.of() : List.copyOf(goals);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public boolean complete() {
        return decision == EvaluationDecision.PASS;
    }
}
