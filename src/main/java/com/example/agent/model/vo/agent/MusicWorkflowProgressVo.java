package com.example.agent.model.vo.agent;

import com.example.agent.agent.contract.MusicWorkflowSnapshot;

import java.util.List;
import java.util.UUID;

public record MusicWorkflowProgressVo(
        UUID workflowId,
        String goal,
        String status,
        List<TaskVo> tasks
) {
    public static MusicWorkflowProgressVo from(MusicWorkflowSnapshot snapshot) {
        return new MusicWorkflowProgressVo(snapshot.workflowId(), snapshot.goal(), snapshot.status().name(),
                snapshot.tasks().stream().map(value -> new TaskVo(
                        value.id(), value.title(), value.assignedAgent(), value.status().name(),
                        value.attempts(), value.maxAttempts(), value.message())).toList());
    }

    public record TaskVo(
            String id,
            String title,
            String assignedAgent,
            String status,
            int attempts,
            int maxAttempts,
            String message
    ) {
    }
}
