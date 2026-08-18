package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.model.bo.AgentActionType;

import java.util.Set;

/** One route's bounded execution, retry and evidence contract. */
public record MusicWorkflowPolicy(
        MusicAutonomyLevel autonomy,
        int maxExecutionAttempts,
        boolean profileAllowed,
        boolean confirmationRequired,
        Set<AgentActionType> requiredEvidence
) {
    public MusicWorkflowPolicy {
        autonomy = autonomy == null ? MusicAutonomyLevel.READ_ONLY : autonomy;
        if (maxExecutionAttempts < 1 || maxExecutionAttempts > 3) {
            throw new IllegalArgumentException("执行尝试次数必须在 1 到 3 之间");
        }
        requiredEvidence = requiredEvidence == null ? Set.of() : Set.copyOf(requiredEvidence);
    }

    public static MusicWorkflowPolicy readOnly(int attempts, boolean profileAllowed,
                                                Set<AgentActionType> requiredEvidence) {
        return new MusicWorkflowPolicy(MusicAutonomyLevel.READ_ONLY, attempts, profileAllowed,
                false, requiredEvidence);
    }

    public static MusicWorkflowPolicy confirmRequired(Set<AgentActionType> requiredEvidence) {
        return new MusicWorkflowPolicy(MusicAutonomyLevel.CONFIRM_REQUIRED, 1, false,
                true, requiredEvidence);
    }

    public boolean retryable() {
        return maxExecutionAttempts > 1 && autonomy == MusicAutonomyLevel.READ_ONLY;
    }
}
