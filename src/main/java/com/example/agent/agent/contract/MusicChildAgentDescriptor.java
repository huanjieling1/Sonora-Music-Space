package com.example.agent.agent.contract;

import java.util.Set;

/** Runtime declaration used by the scheduler; adding an agent does not change the scheduler. */
public record MusicChildAgentDescriptor(
        String id,
        String displayName,
        Set<String> capabilities,
        int priority
) {
    public MusicChildAgentDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("子 Agent 标识不能为空");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("子 Agent 名称不能为空");
        id = id.strip();
        displayName = displayName.strip();
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
        if (capabilities.isEmpty()) throw new IllegalArgumentException("子 Agent 必须声明至少一项能力");
    }

    public boolean supports(String capabilityId) {
        return capabilityId != null && capabilities.contains(capabilityId);
    }
}
