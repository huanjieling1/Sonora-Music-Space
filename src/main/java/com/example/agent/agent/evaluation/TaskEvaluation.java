package com.example.agent.agent.evaluation;

import java.util.List;

/** Immutable task-level acceptance result consumed by the DAG runtime. */
public record TaskEvaluation(
        String taskId,
        EvaluationDecision decision,
        List<EvaluationFinding> findings,
        String correction,
        String waitingSlot
) {
    public TaskEvaluation {
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("任务验收必须关联任务");
        taskId = taskId.strip();
        decision = decision == null ? EvaluationDecision.FAIL : decision;
        findings = findings == null ? List.of() : List.copyOf(findings);
        correction = correction == null ? "" : correction.strip();
        waitingSlot = waitingSlot == null ? "" : waitingSlot.strip();
        if (decision == EvaluationDecision.PASS && !findings.isEmpty()) {
            throw new IllegalArgumentException("PASS 任务验收不能包含失败发现");
        }
        if (decision == EvaluationDecision.ASK_USER && waitingSlot.isEmpty()) {
            throw new IllegalArgumentException("ASK_USER 必须声明等待槽位");
        }
    }

    public static TaskEvaluation pass(String taskId) {
        return new TaskEvaluation(taskId, EvaluationDecision.PASS, List.of(), "", "");
    }
}
