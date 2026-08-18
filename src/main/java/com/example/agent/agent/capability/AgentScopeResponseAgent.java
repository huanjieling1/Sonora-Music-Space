package com.example.agent.agent.capability;

import org.springframework.stereotype.Component;

/** Deterministic boundary response for unsupported or ambiguous requests. */
@Component
public class AgentScopeResponseAgent {
    private final AgentCapabilityRegistry registry;

    public AgentScopeResponseAgent(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public String outOfScope() {
        return registry.outOfScopeAnswer();
    }

    public String clarify() {
        return registry.clarificationAnswer();
    }
}
