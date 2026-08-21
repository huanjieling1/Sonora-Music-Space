package com.example.agent.orchestration.dag;

import com.example.agent.agent.contract.planning.TypedTaskResult;

/** Capability adapter outcome understood by the generic DAG runtime. */
public record DagTaskOutcome(
        Kind kind,
        TypedTaskResult result,
        String errorCode,
        String message,
        boolean retryable,
        String waitingSlot
) {
    public DagTaskOutcome {
        kind = kind == null ? Kind.FAILURE : kind;
        errorCode = errorCode == null ? "" : errorCode.strip();
        message = message == null ? "" : message.strip();
        waitingSlot = waitingSlot == null ? "" : waitingSlot.strip();
        if (kind == Kind.SUCCESS && (result == null || !result.successful())) {
            throw new IllegalArgumentException("成功结果必须携带 successful TypedTaskResult");
        }
        if (kind == Kind.WAITING_USER && waitingSlot.isEmpty()) {
            throw new IllegalArgumentException("WAITING_USER 必须声明等待槽位");
        }
    }

    public enum Kind { SUCCESS, FAILURE, WAITING_USER }

    public static DagTaskOutcome success(TypedTaskResult result) {
        return new DagTaskOutcome(Kind.SUCCESS, result, "", "", false, "");
    }

    public static DagTaskOutcome failure(String code, String message, boolean retryable) {
        return new DagTaskOutcome(Kind.FAILURE, null, code, message, retryable, "");
    }

    public static DagTaskOutcome waiting(String slot, String question) {
        return new DagTaskOutcome(Kind.WAITING_USER, null, "WAITING_USER", question, false, slot);
    }
}
