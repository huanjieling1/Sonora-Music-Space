package com.example.agent.agent.contract.planning;

import java.util.List;
import java.util.Map;

/** One independently verifiable user objective in a potentially multi-goal request. */
public record GoalNode(
        String id,
        String title,
        GoalOperation operation,
        GoalTargetType targetType,
        Map<String, ValueExpression> inputs,
        List<GoalConstraint> constraints,
        List<AcceptanceCriterion> acceptanceCriteria,
        List<String> missingSlots,
        boolean requiresConfirmation
) {
    public GoalNode {
        id = PlanningModelSupport.requiredText(id, "目标标识不能为空");
        title = PlanningModelSupport.requiredText(title, "目标标题不能为空");
        operation = operation == null ? GoalOperation.UNKNOWN : operation;
        targetType = targetType == null ? GoalTargetType.NONE : targetType;
        inputs = PlanningModelSupport.map(inputs);
        constraints = PlanningModelSupport.list(constraints);
        acceptanceCriteria = PlanningModelSupport.list(acceptanceCriteria);
        missingSlots = PlanningModelSupport.strings(missingSlots);
    }
}
