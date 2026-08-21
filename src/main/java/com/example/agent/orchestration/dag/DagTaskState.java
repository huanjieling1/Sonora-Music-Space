package com.example.agent.orchestration.dag;

import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.evaluation.TaskEvaluation;
import com.example.agent.orchestration.confirmation.ConfirmationRequest;

/** Persistable state of one compiled task. */
public record DagTaskState(
        String taskId,
        DagTaskStatus status,
        int attempts,
        String message,
        String waitingSlot,
        String idempotencyKey,
        TypedTaskResult result,
        TaskEvaluation evaluation,
        String errorCode,
        boolean replanPending,
        ConfirmationRequest confirmationRequest
) {
    public DagTaskState(String taskId, DagTaskStatus status, int attempts, String message,
                        String waitingSlot, String idempotencyKey, TypedTaskResult result) {
        this(taskId, status, attempts, message, waitingSlot, idempotencyKey, result, null,
                "", false, null);
    }

    public DagTaskState(String taskId, DagTaskStatus status, int attempts, String message,
                        String waitingSlot, String idempotencyKey, TypedTaskResult result,
                        TaskEvaluation evaluation) {
        this(taskId, status, attempts, message, waitingSlot, idempotencyKey, result,
                evaluation, "", false, null);
    }

    public DagTaskState(String taskId, DagTaskStatus status, int attempts, String message,
                        String waitingSlot, String idempotencyKey, TypedTaskResult result,
                        TaskEvaluation evaluation, String errorCode, boolean replanPending) {
        this(taskId, status, attempts, message, waitingSlot, idempotencyKey, result,
                evaluation, errorCode, replanPending, null);
    }

    public DagTaskState {
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("DAG 任务 ID 不能为空");
        taskId = taskId.strip();
        status = status == null ? DagTaskStatus.PENDING : status;
        if (attempts < 0) throw new IllegalArgumentException("任务尝试次数不能为负数");
        message = message == null ? "" : message.strip();
        waitingSlot = waitingSlot == null ? "" : waitingSlot.strip();
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.strip();
        errorCode = errorCode == null ? "" : errorCode.strip();
    }
}
