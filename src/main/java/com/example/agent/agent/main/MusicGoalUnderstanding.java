package com.example.agent.agent.main;

import com.example.agent.agent.capability.AgentScopeDecision;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import com.example.agent.agent.contract.MusicTurnPlan;

import java.util.List;

/** Verified hand-off from the goal-understanding stage to the main supervisory agent. */
public record MusicGoalUnderstanding(
        MusicIntentUnderstanding understanding,
        MusicAgentRoute route,
        AgentScopeDecision scope,
        boolean usesProfile,
        boolean usedCapabilityGateway,
        MusicTurnPlan followUpPlan,
        MusicSupportContext supportContext,
        MusicSupportSuggestionPlan supportPlan,
        String arbitrationReason,
        List<String> evidence
) {
    public MusicGoalUnderstanding {
        if (understanding == null || route == null || scope == null) {
            throw new IllegalArgumentException("目标理解结果不完整");
        }
        supportContext = supportContext == null ? MusicSupportContext.none() : supportContext;
        arbitrationReason = arbitrationReason == null ? "" : arbitrationReason.strip();
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
