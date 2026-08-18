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
        Set<String> activationTerms,
        String instructions,
        String source,
        AgentSkillSupportAffordance supportAffordance) {

    public AgentSkillDefinition(String id, String name, String description, int priority,
                                Set<String> tools, Set<String> activationTerms,
                                String instructions, String source) {
        this(id, name, description, priority, tools, activationTerms, instructions, source,
                AgentSkillSupportAffordance.disabled());
    }

    public AgentSkillDefinition {
        id = requireText(id, "Skill id");
        if (!id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Skill id must use lowercase hyphen-case: " + id);
        }
        name = requireText(name, "Skill name");
        description = requireText(description, "Skill description");
        instructions = requireText(instructions, "Skill instructions");
        source = requireText(source, "Skill source");
        supportAffordance = supportAffordance == null
                ? AgentSkillSupportAffordance.disabled() : supportAffordance;
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
        LinkedHashSet<String> normalizedTerms = new LinkedHashSet<>();
        if (activationTerms != null) {
            for (String term : activationTerms) {
                normalizedTerms.add(requireText(term, "Activation term in skill " + id));
            }
        }
        if (normalizedTerms.isEmpty()) {
            normalizedTerms.add(name);
        }
        activationTerms = Collections.unmodifiableSet(normalizedTerms);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value.trim();
    }
}
