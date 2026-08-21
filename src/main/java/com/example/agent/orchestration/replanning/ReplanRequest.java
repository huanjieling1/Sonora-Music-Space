package com.example.agent.orchestration.replanning;

import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.orchestration.dag.DagTaskState;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Complete, bounded context given to a local subgraph replanning strategy. */
public record ReplanRequest(
        UUID workflowId,
        CompiledPlan currentPlan,
        String failedTaskId,
        String errorCode,
        String errorMessage,
        int replanAttempt,
        Set<String> failedSubgraphTaskIds,
        Map<String, TypedTaskResult> preservedResults,
        Map<String, List<AcceptanceCriterion>> acceptanceCriteria,
        Map<String, DagTaskState> taskStates,
        Map<String, Object> userInputs,
        Set<String> previousPlanFingerprints,
        Set<String> replayApprovedTaskIds
) {
    public ReplanRequest {
        if (workflowId == null || currentPlan == null) throw new IllegalArgumentException("重规划工作流和计划不能为空");
        if (failedTaskId == null || failedTaskId.isBlank()) throw new IllegalArgumentException("失败任务不能为空");
        failedTaskId = failedTaskId.strip();
        errorCode = errorCode == null || errorCode.isBlank() ? "TASK_FAILED" : errorCode.strip();
        errorMessage = errorMessage == null ? "" : errorMessage.strip();
        if (replanAttempt < 1) throw new IllegalArgumentException("重规划次数必须从 1 开始");
        failedSubgraphTaskIds = failedSubgraphTaskIds == null ? Set.of() : Set.copyOf(failedSubgraphTaskIds);
        preservedResults = preservedResults == null ? Map.of() : Map.copyOf(preservedResults);
        acceptanceCriteria = acceptanceCriteria == null ? Map.of() : Map.copyOf(acceptanceCriteria);
        taskStates = taskStates == null ? Map.of() : Map.copyOf(taskStates);
        userInputs = userInputs == null ? Map.of() : Map.copyOf(userInputs);
        previousPlanFingerprints = previousPlanFingerprints == null ? Set.of() : Set.copyOf(previousPlanFingerprints);
        replayApprovedTaskIds = replayApprovedTaskIds == null ? Set.of() : Set.copyOf(replayApprovedTaskIds);
    }
}
