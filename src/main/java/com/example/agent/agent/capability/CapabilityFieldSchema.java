package com.example.agent.agent.capability;

import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.ValueType;

/** One named field in a planner-facing capability input or output object. */
public record CapabilityFieldSchema(
        ValueType type,
        boolean required,
        String description,
        boolean sensitive,
        GoalTargetType entityType,
        ValueType itemType
) {
    public CapabilityFieldSchema {
        type = type == null ? ValueType.ANY : type;
        description = description == null ? "" : description.strip();
        entityType = entityType == null ? GoalTargetType.NONE : entityType;
        itemType = itemType == null ? ValueType.ANY : itemType;
        if (type != ValueType.ARRAY && itemType != ValueType.ANY) {
            throw new IllegalArgumentException("只有 ARRAY 字段可以声明 itemType");
        }
        if (type != ValueType.ENTITY && entityType != GoalTargetType.NONE) {
            throw new IllegalArgumentException("只有 ENTITY 字段可以声明 entityType");
        }
    }

    public static CapabilityFieldSchema required(ValueType type, String description) {
        return new CapabilityFieldSchema(type, true, description, false, GoalTargetType.NONE, ValueType.ANY);
    }

    public static CapabilityFieldSchema optional(ValueType type, String description) {
        return new CapabilityFieldSchema(type, false, description, false, GoalTargetType.NONE, ValueType.ANY);
    }

    public static CapabilityFieldSchema array(boolean required, ValueType itemType, String description) {
        return new CapabilityFieldSchema(ValueType.ARRAY, required, description, false,
                GoalTargetType.NONE, itemType);
    }

    public static CapabilityFieldSchema entity(boolean required, GoalTargetType entityType, String description) {
        return new CapabilityFieldSchema(ValueType.ENTITY, required, description, false,
                entityType, ValueType.ANY);
    }
}
