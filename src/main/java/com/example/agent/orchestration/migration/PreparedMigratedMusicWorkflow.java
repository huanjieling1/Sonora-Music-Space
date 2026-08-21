package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.orchestration.observability.PlannerRolloutPolicy;

import java.util.UUID;

/** A fully validated plan that has not executed any capability yet. */
public record PreparedMigratedMusicWorkflow(
        UUID workflowId,
        MusicAgentTurn turn,
        UserGoalGraph goalGraph,
        MusicTurnPlan followUpPlan,
        CompiledPlan plan,
        PlannerRolloutPolicy.Decision rollout
) {
    public PreparedMigratedMusicWorkflow {
        if (workflowId == null || turn == null || goalGraph == null || plan == null || rollout == null) {
            throw new IllegalArgumentException("动态工作流准备结果不完整");
        }
        followUpPlan = followUpPlan == null ? MusicTurnPlan.none() : followUpPlan;
    }
}
