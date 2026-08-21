package com.example.agent.agent.contract.planning;

/** A user-visible condition that must be preserved while compiling a goal into tasks. */
public record GoalConstraint(
        String field,
        Operator operator,
        ValueExpression expected,
        boolean hard,
        String description
) {
    public GoalConstraint {
        field = PlanningModelSupport.requiredText(field, "约束字段不能为空");
        operator = operator == null ? Operator.EQUALS : operator;
        description = PlanningModelSupport.text(description);
    }

    public enum Operator {
        EQUALS,
        NOT_EQUALS,
        IN,
        NOT_IN,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        CONTAINS,
        MATCHES,
        EXISTS
    }
}
