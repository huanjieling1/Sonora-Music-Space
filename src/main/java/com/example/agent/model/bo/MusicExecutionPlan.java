package com.example.agent.model.bo;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Validated execution contract compiled from the model-facing MusicSearchPlan.
 * Provider code consumes this type rather than executing arbitrary model output.
 */
public record MusicExecutionPlan(
        String description,
        MusicSearchIntent intent,
        MusicHardConstraints hardConstraints,
        MusicSoftIntent softIntent,
        MusicIntentHints hints,
        List<MusicToolCall> toolCalls,
        double confidence,
        String clarificationQuestion
) {
    public MusicExecutionPlan {
        description = description == null ? "" : description;
        intent = intent == null ? MusicSearchIntent.AMBIGUOUS : intent;
        hardConstraints = hardConstraints == null
                ? new MusicHardConstraints(null, List.of(), null) : hardConstraints;
        softIntent = softIntent == null ? new MusicSoftIntent("", List.of()) : softIntent;
        hints = hints == null ? new MusicIntentHints(List.of(), List.of(), List.of(), List.of()) : hints;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        confidence = Math.max(0, Math.min(1, confidence));

        if (toolCalls.size() > 4) {
            throw new IllegalArgumentException("Music execution plan supports at most four tool calls");
        }
        Set<String> ids = new HashSet<>();
        for (MusicToolCall call : toolCalls) {
            if (!ids.add(call.id())) {
                throw new IllegalArgumentException("Duplicate music tool call id: " + call.id());
            }
            if (!ids.containsAll(call.dependsOn())) {
                throw new IllegalArgumentException("Music tool dependencies must reference earlier calls");
            }
        }
    }

    public Optional<MusicToolCall> tool(MusicToolName name) {
        return toolCalls.stream().filter(call -> call.name() == name).findFirst();
    }
}
