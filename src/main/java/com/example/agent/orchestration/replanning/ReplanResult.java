package com.example.agent.orchestration.replanning;

import com.example.agent.agent.contract.planning.CompiledPlan;

import java.util.Set;

/** Validated local replanning result returned to the DAG state machine. */
public record ReplanResult(
        Kind kind,
        CompiledPlan updatedPlan,
        Set<String> replacedTaskIds,
        Set<String> preservedTaskIds,
        String planFingerprint,
        String waitingSlot,
        String message
) {
    public ReplanResult {
        kind = kind == null ? Kind.FAIL : kind;
        replacedTaskIds = replacedTaskIds == null ? Set.of() : Set.copyOf(replacedTaskIds);
        preservedTaskIds = preservedTaskIds == null ? Set.of() : Set.copyOf(preservedTaskIds);
        planFingerprint = planFingerprint == null ? "" : planFingerprint.strip();
        waitingSlot = waitingSlot == null ? "" : waitingSlot.strip();
        message = message == null ? "" : message.strip();
        if (kind == Kind.APPLIED && updatedPlan == null) throw new IllegalArgumentException("已应用方案必须包含新计划");
    }

    public enum Kind { APPLIED, ASK_USER, FAIL }
}
