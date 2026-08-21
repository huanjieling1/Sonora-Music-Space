package com.example.agent.agent.planner;

import java.util.List;
import java.util.stream.Collectors;

/** Raised by the compiler when any validator issue remains. */
public final class PlanValidationException extends IllegalArgumentException {
    private final List<PlanValidationIssue> issues;

    public PlanValidationException(List<PlanValidationIssue> issues) {
        super(message(issues));
        this.issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public List<PlanValidationIssue> issues() {
        return issues;
    }

    private static String message(List<PlanValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) return "计划验证失败";
        return "计划验证失败：" + issues.stream().limit(5)
                .map(value -> value.code() + "(" + value.message() + ")")
                .collect(Collectors.joining("；"));
    }
}
