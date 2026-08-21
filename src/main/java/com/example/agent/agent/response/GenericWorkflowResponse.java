package com.example.agent.agent.response;

import com.example.agent.agent.evaluation.GroundedClaim;
import com.example.agent.orchestration.dag.DagWorkflowStatus;

import java.util.List;
import java.util.UUID;

/** Guarded final response for one dynamic workflow. */
public record GenericWorkflowResponse(
        UUID workflowId,
        DagWorkflowStatus workflowStatus,
        String answer,
        List<GoalResponseSection> goals,
        List<ResponseCardAction> actions,
        boolean guarded
) {
    public GenericWorkflowResponse {
        if (workflowId == null) throw new IllegalArgumentException("最终响应必须关联工作流");
        workflowStatus = workflowStatus == null ? DagWorkflowStatus.FAILED : workflowStatus;
        answer = answer == null ? "" : answer.strip();
        goals = goals == null ? List.of() : List.copyOf(goals);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public List<GroundedClaim> claims() {
        return goals.stream().flatMap(goal -> goal.facts().stream()).map(GroundedResponseFact::claim).toList();
    }
}
