package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.response.GenericWorkflowResponse;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.orchestration.dag.DagExecutionSnapshot;

import java.util.List;

/** Complete dynamic migration result, independent from the legacy Route response model. */
public record MigratedMusicWorkflowResult(
        UserGoalGraph goalGraph,
        DagExecutionSnapshot snapshot,
        GenericWorkflowResponse response,
        List<AgentActionBo> actions
) {
    public MigratedMusicWorkflowResult(UserGoalGraph goalGraph, DagExecutionSnapshot snapshot,
                                       GenericWorkflowResponse response) {
        this(goalGraph, snapshot, response, List.of());
    }

    public MigratedMusicWorkflowResult {
        if (goalGraph == null || snapshot == null || response == null) {
            throw new IllegalArgumentException("迁移工作流结果不完整");
        }
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
