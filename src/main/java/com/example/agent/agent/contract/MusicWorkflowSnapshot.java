package com.example.agent.agent.contract;

import java.util.List;
import java.util.UUID;

public record MusicWorkflowSnapshot(
        UUID workflowId,
        String goal,
        MusicWorkflowStatus status,
        List<MusicWorkflowTaskSnapshot> tasks
) {
    public MusicWorkflowSnapshot {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
    }
}
