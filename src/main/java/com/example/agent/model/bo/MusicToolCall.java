package com.example.agent.model.bo;

import java.util.List;

public record MusicToolCall(
        String id,
        MusicToolName name,
        List<MusicSearchTask> tasks,
        List<String> dependsOn
) {
    public MusicToolCall {
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,47}")) {
            throw new IllegalArgumentException("Invalid music tool call id");
        }
        if (name == null) {
            throw new IllegalArgumentException("Music tool name is required");
        }
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Music search tool requires at least one task");
        }
        if (dependsOn.contains(id)) {
            throw new IllegalArgumentException("Music tool call cannot depend on itself");
        }
    }
}
