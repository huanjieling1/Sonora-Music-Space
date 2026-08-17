package com.example.agent.skill;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable application-level skill loaded from an agent-skills resource folder.
 */
public record AgentSkillDefinition(
        String id,
        String name,
        String description,
        int priority,
        Set<String> tools,
        String instructions,
        String source) {

    public AgentSkillDefinition {
        id = requireText(id, "Skill id");
        if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Skill id must use lowercase hyphen-case: " + id);
        }
        name = requireText(name, "Skill name");
        description = requireText(description, "Skill description");
        instructions = requireText(instructions, "Skill instructions");
        source = requireText(source, "Skill source");
        if (priority < 0 || priority > 1000) {
            throw new IllegalArgumentException("Skill priority must be between 0 and 1000: " + id);
        }
        if (tools == null || tools.isEmpty()) {
            throw new IllegalArgumentException("Skill must bind at least one tool: " + id);
        }
        LinkedHashSet<String> normalizedTools = new LinkedHashSet<>();
        for (String tool : tools) {
            normalizedTools.add(requireText(tool, "Tool name in skill " + id));
        }
        tools = Collections.unmodifiableSet(normalizedTools);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
