package com.example.agent.orchestration.dag;

import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.orchestration.replanning.ReplanRecord;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable persisted workflow snapshot used for UI refresh and process recovery. */
public record DagExecutionSnapshot(
        UUID workflowId,
        String principalId,
        String conversationId,
        CompiledPlan plan,
        DagWorkflowStatus status,
        List<DagTaskState> tasks,
        Map<String, Object> userInputs,
        Instant createdAt,
        Instant updatedAt,
        List<ReplanRecord> replanRecords
) {
    public DagExecutionSnapshot(UUID workflowId, String principalId, String conversationId,
                                CompiledPlan plan, DagWorkflowStatus status, List<DagTaskState> tasks,
                                Map<String, Object> userInputs, Instant createdAt, Instant updatedAt) {
        this(workflowId, principalId, conversationId, plan, status, tasks, userInputs,
                createdAt, updatedAt, List.of());
    }

    public DagExecutionSnapshot {
        if (workflowId == null || plan == null) throw new IllegalArgumentException("工作流和编译计划不能为空");
        if (principalId == null || principalId.isBlank()) throw new IllegalArgumentException("工作流用户不能为空");
        principalId = principalId.strip();
        conversationId = conversationId == null ? "" : conversationId.strip();
        status = status == null ? DagWorkflowStatus.RUNNING : status;
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        userInputs = userInputs == null ? Map.of() : Map.copyOf(userInputs);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
        replanRecords = replanRecords == null ? List.of() : List.copyOf(replanRecords);
    }
}
