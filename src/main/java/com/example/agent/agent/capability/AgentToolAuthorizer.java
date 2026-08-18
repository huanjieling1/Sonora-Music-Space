package com.example.agent.agent.capability;

import org.springframework.stereotype.Component;

/** Runtime role/tool authorization independent of prompts and model output. */
@Component
public class AgentToolAuthorizer {
    private final AgentCapabilityRegistry registry;

    public AgentToolAuthorizer(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public void requireAllowed(AgentRole role, String toolName) {
        boolean allowed = role == AgentRole.EXECUTION && registry.supportsTool(toolName);
        if (!allowed) {
            throw new SecurityException("Agent role " + role + " is not allowed to invoke tool " + toolName);
        }
    }
}
