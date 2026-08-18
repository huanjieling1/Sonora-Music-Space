package com.example.agent.agent.contract;

/** A capability-backed follow-up the UI can send as the user's next turn. */
public record MusicProactiveSuggestion(
        String label,
        String prompt,
        String capabilityId,
        boolean requiresConfirmation
) {
    public MusicProactiveSuggestion {
        label = normalize(label, 24);
        prompt = normalize(prompt, 240);
        capabilityId = normalize(capabilityId, 80);
        if (label.isBlank() || prompt.isBlank() || capabilityId.isBlank()) {
            throw new IllegalArgumentException("主动建议必须包含标签、提示词和能力标识");
        }
    }

    private static String normalize(String value, int limit) {
        String safe = value == null ? "" : value.strip();
        return safe.substring(0, Math.min(limit, safe.length()));
    }
}
