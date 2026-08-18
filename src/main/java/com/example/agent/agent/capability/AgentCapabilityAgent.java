package com.example.agent.agent.capability;

import org.springframework.stereotype.Component;

/** Deterministic capability response; it never asks a model what the application can do. */
@Component
public class AgentCapabilityAgent {
    private final AgentCapabilityRegistry registry;

    public AgentCapabilityAgent(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public String answer() {
        return registry.capabilityAnswer();
    }
}
