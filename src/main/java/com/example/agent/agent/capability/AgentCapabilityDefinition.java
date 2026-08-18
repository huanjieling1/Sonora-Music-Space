package com.example.agent.agent.capability;

import java.util.Set;

/** One runtime capability backed by a loaded Skill or a capability contributor. */
public record AgentCapabilityDefinition(
        String id,
        String name,
        String description,
        Set<String> tools,
        Set<String> activationTerms,
        String source
) {
    public AgentCapabilityDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("能力标识不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("能力名称不能为空");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("能力说明不能为空");
        id = id.strip();
        name = name.strip();
        description = description.strip();
        tools = tools == null ? Set.of() : Set.copyOf(tools);
        activationTerms = activationTerms == null ? Set.of() : Set.copyOf(activationTerms);
        source = source == null || source.isBlank() ? "runtime" : source.strip();
    }
}
