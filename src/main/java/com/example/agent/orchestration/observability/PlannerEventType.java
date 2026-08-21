package com.example.agent.orchestration.observability;

public enum PlannerEventType {
    GOAL_GRAPH, COMPILED_PLAN, TASK_STARTED, TASK_FINISHED,
    TASK_EVALUATION, REPLAN, ROLLOUT_DECISION, PLANNING_REJECTED, ALERT
}
