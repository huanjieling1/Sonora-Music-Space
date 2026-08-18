package com.example.agent.agent.contract;

public record MusicWorkflowTaskSnapshot(
        String id,
        String title,
        String assignedAgent,
        MusicWorkflowTaskStatus status,
        int attempts,
        int maxAttempts,
        String message
) {
    public MusicWorkflowTaskSnapshot {
        message = message == null ? "" : message.strip();
    }
}
