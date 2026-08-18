package com.example.agent.agent.contract;

import java.util.List;

/** Uniform result envelope used between the scheduler and every child agent. */
public record MusicTaskResult(
        String taskId,
        boolean successful,
        Object payload,
        List<MusicTaskEvidence> evidence,
        String summary,
        String errorCode
) {
    public MusicTaskResult {
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("任务标识不能为空");
        taskId = taskId.strip();
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        summary = summary == null ? "" : summary.strip();
        errorCode = errorCode == null ? "" : errorCode.strip();
    }

    public <T> T payloadAs(Class<T> type) {
        if (payload == null) return null;
        if (!type.isInstance(payload)) {
            throw new IllegalStateException("任务 " + taskId + " 返回了错误的结果类型："
                    + payload.getClass().getSimpleName() + "，期望 " + type.getSimpleName());
        }
        return type.cast(payload);
    }
}
