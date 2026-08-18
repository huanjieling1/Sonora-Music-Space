package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentWorkflowState;

public record MusicWorkflowOutcome(MusicAgentWorkflowState state, boolean successful) {
    public MusicWorkflowOutcome {
        if (state == null) throw new IllegalArgumentException("工作流结果状态不能为空");
    }

    public static MusicWorkflowOutcome success(MusicAgentWorkflowState state) {
        return new MusicWorkflowOutcome(state, true);
    }
}
