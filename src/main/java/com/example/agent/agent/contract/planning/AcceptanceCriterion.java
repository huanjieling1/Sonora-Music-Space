package com.example.agent.agent.contract.planning;

import java.util.Map;

/** Machine-checkable success condition attached to a goal or a planned task. */
public record AcceptanceCriterion(
        String id,
        Type type,
        String subject,
        ValueExpression expected,
        boolean required,
        String description,
        Map<String, String> attributes
) {
    public AcceptanceCriterion {
        id = PlanningModelSupport.requiredText(id, "验收条件标识不能为空");
        type = type == null ? Type.CUSTOM : type;
        subject = PlanningModelSupport.requiredText(subject, "验收对象不能为空");
        description = PlanningModelSupport.text(description);
        attributes = PlanningModelSupport.map(attributes);
    }

    public enum Type {
        OUTPUT_PRESENT,
        OUTPUT_TYPE,
        ENTITY_MATCH,
        COUNT,
        SOURCE,
        CONSTRAINT,
        STATE_CHANGE,
        GOAL_COVERAGE,
        CUSTOM
    }
}
