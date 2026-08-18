package com.example.agent.orchestration.runtime;

import com.example.agent.agent.capability.AgentScopeDecision;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.orchestration.MusicWorkflowRun;

/** Immutable hand-off from semantic routing to the selected runtime workflow strategy. */
public record MusicWorkflowExecutionContext(
        MusicAgentTurn turn,
        MusicAgentRoute route,
        MusicIntentUnderstanding understanding,
        AgentScopeDecision scope,
        boolean usesProfile,
        MusicTurnPlan followUpPlan,
        MusicSupportContext supportContext,
        MusicSupportSuggestionPlan supportPlan,
        MusicAgentWorkflowState state,
        MusicWorkflowRun run
) {
    public MusicWorkflowExecutionContext {
        if (turn == null || route == null || state == null || run == null) {
            throw new IllegalArgumentException("运行时工作流上下文不完整");
        }
        supportContext = supportContext == null ? MusicSupportContext.none() : supportContext;
    }
}
