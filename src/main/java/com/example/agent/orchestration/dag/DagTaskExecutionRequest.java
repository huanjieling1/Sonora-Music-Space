package com.example.agent.orchestration.dag;

import com.example.agent.agent.contract.planning.PlanTask;

import java.util.Map;
import java.util.UUID;

public record DagTaskExecutionRequest(
        UUID workflowId,
        String principalId,
        PlanTask task,
        Map<String, Object> resolvedInputs,
        int attempt,
        String idempotencyKey
) {
    public DagTaskExecutionRequest {
        if (workflowId == null || task == null) throw new IllegalArgumentException("任务执行请求不完整");
        if (principalId == null || principalId.isBlank()) throw new IllegalArgumentException("执行用户不能为空");
        principalId = principalId.strip();
        resolvedInputs = resolvedInputs == null ? Map.of() : Map.copyOf(resolvedInputs);
        if (attempt < 1) throw new IllegalArgumentException("执行尝试次数必须为正数");
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.strip();
    }
}
