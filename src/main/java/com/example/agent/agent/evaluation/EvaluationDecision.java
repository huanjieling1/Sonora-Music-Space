package com.example.agent.agent.evaluation;

/** Control signal emitted by both task-level and goal-level evaluators. */
public enum EvaluationDecision {
    PASS(0),
    REVISE(1),
    REPLAN(2),
    ASK_USER(3),
    FAIL(4);

    private final int severity;

    EvaluationDecision(int severity) {
        this.severity = severity;
    }

    public static EvaluationDecision combine(EvaluationDecision left, EvaluationDecision right) {
        if (left == null) return right == null ? PASS : right;
        if (right == null) return left;
        return left.severity >= right.severity ? left : right;
    }
}
