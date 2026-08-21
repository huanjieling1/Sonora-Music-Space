package com.example.agent.agent.evaluation.benchmark;

import java.util.List;

/** Four release-gating metrics plus sample-level diagnostics. Rates are percentages in [0, 100]. */
public record PlannerEvaluationReport(
        int benchmarkCases,
        int exactlyDecomposedCases,
        int compilableCases,
        int satisfiableGoals,
        int completedSatisfiableGoals,
        int unsatisfiedGoals,
        int falseSuccessGoals,
        double goalDecompositionAccuracy,
        double planCompilability,
        double goalCompletionRate,
        double falseSuccessRate,
        List<String> decompositionFailures,
        List<String> compilationFailures
) {
    public PlannerEvaluationReport {
        decompositionFailures = decompositionFailures == null ? List.of() : List.copyOf(decompositionFailures);
        compilationFailures = compilationFailures == null ? List.of() : List.copyOf(compilationFailures);
    }
}
