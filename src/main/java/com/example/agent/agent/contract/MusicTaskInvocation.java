package com.example.agent.agent.contract;

import java.util.Map;

/** Explicit task hand-off; no ThreadLocal context is required by child-agent orchestration. */
public record MusicTaskInvocation(
        MusicWorkflowTaskSpec task,
        MusicAgentTurn turn,
        MusicAgentRoute route,
        UserTasteContext tasteContext,
        Map<String, Object> inputs
) {
    public MusicTaskInvocation {
        if (task == null || turn == null || route == null) {
            throw new IllegalArgumentException("子 Agent 任务上下文不完整");
        }
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }

    public MusicTaskInvocation withTurn(MusicAgentTurn correctedTurn) {
        return new MusicTaskInvocation(task, correctedTurn, route, tasteContext, inputs);
    }
}
