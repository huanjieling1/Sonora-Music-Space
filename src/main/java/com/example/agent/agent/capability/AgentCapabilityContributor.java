package com.example.agent.agent.capability;

import java.util.List;

/**
 * Extension point for capabilities that are not represented by a tool-bound Skill.
 * A feature module contributes its own metadata; no central catalog needs editing.
 */
public interface AgentCapabilityContributor {
    List<AgentCapabilityDefinition> capabilities();
}
