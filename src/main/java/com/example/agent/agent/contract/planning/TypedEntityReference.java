package com.example.agent.agent.contract.planning;

import java.util.List;

/** Canonical entity identity carried beside task output for later grounding and evaluation. */
public record TypedEntityReference(
        GoalTargetType entityType,
        String canonicalName,
        String provider,
        String entityId,
        List<String> aliases
) {
    public TypedEntityReference(GoalTargetType entityType, String canonicalName,
                                String provider, String entityId) {
        this(entityType, canonicalName, provider, entityId, List.of());
    }

    public TypedEntityReference {
        entityType = entityType == null ? GoalTargetType.NONE : entityType;
        canonicalName = PlanningModelSupport.requiredText(canonicalName, "实体规范名称不能为空");
        provider = PlanningModelSupport.requiredText(provider, "实体 provider 不能为空");
        entityId = PlanningModelSupport.requiredText(entityId, "实体 ID 不能为空");
        aliases = aliases == null ? List.of() : aliases.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip).distinct().toList();
    }
}
