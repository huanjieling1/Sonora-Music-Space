package com.example.agent.agent.contract.planning;

import java.util.List;
import java.util.UUID;

/** Structured meaning of one user turn before any concrete capability is selected. */
public record UserGoalGraph(
        String schemaVersion,
        UUID graphId,
        String originalRequest,
        List<GoalNode> goals,
        List<GoalRelation> relations
) {
    public UserGoalGraph {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.strip();
        graphId = graphId == null ? UUID.randomUUID() : graphId;
        originalRequest = PlanningModelSupport.requiredText(originalRequest, "用户原始请求不能为空");
        goals = PlanningModelSupport.list(goals);
        if (goals.isEmpty()) throw new IllegalArgumentException("目标图至少需要一个目标");
        relations = PlanningModelSupport.list(relations);
    }
}
