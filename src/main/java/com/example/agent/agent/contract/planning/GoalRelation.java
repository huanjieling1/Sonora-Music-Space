package com.example.agent.agent.contract.planning;

/** Directed relationship: targetGoalId is interpreted relative to sourceGoalId. */
public record GoalRelation(
        String sourceGoalId,
        String targetGoalId,
        Type type,
        ValueExpression condition,
        String description
) {
    public GoalRelation {
        sourceGoalId = PlanningModelSupport.requiredText(sourceGoalId, "关系源目标不能为空");
        targetGoalId = PlanningModelSupport.requiredText(targetGoalId, "关系目标不能为空");
        if (sourceGoalId.equals(targetGoalId)) throw new IllegalArgumentException("目标不能依赖自身");
        type = type == null ? Type.DEPENDS_ON : type;
        description = PlanningModelSupport.text(description);
        if (type == Type.CONDITIONAL && condition == null) {
            throw new IllegalArgumentException("条件关系必须声明 condition");
        }
    }

    public enum Type {
        /** target starts only after source has completed successfully. */
        DEPENDS_ON,
        /** target follows source to preserve an explicit user-requested order. */
        SEQUENCE,
        /** source and target may execute concurrently when their other dependencies are ready. */
        PARALLEL,
        /** target is enabled only when condition evaluates to true. */
        CONDITIONAL
    }
}
