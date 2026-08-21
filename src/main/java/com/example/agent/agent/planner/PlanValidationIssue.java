package com.example.agent.agent.planner;

/** One stable, machine-readable reason why a draft cannot enter execution. */
public record PlanValidationIssue(String code, String taskId, String goalId, String message) {
    public PlanValidationIssue {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("验证问题码不能为空");
        code = code.strip();
        taskId = taskId == null ? "" : taskId.strip();
        goalId = goalId == null ? "" : goalId.strip();
        message = message == null ? "" : message.strip();
    }
}
