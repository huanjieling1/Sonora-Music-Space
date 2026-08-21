package com.example.agent.agent.capability;

/** Declarative condition checked before a planned capability may run. */
public record CapabilityPrecondition(
        String id,
        Type type,
        boolean required,
        String description
) {
    public CapabilityPrecondition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("能力前置条件标识不能为空");
        id = id.strip();
        type = type == null ? Type.CUSTOM : type;
        description = description == null ? "" : description.strip();
    }

    public enum Type {
        AUTHENTICATED_USER,
        PROFILE_AVAILABLE,
        QQ_SESSION_AVAILABLE,
        RECENT_SEARCH_RESULTS,
        ENTITY_AVAILABLE,
        EXPLICIT_USER_INTENT,
        CUSTOM
    }
}
