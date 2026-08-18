package com.example.agent.agent.contract;

import com.example.agent.model.bo.AgentActionType;

import java.util.Set;

public record MusicExecutionResult(MusicAgentRoute route, boolean successful, String factualAnswer,
                                   Set<AgentActionType> evidenceTypes, Outcome outcome) {
    public MusicExecutionResult(MusicAgentRoute route, boolean successful, String factualAnswer) {
        this(route, successful, factualAnswer, Set.of(), successful ? Outcome.COMPLETE : Outcome.FAILED);
    }

    public MusicExecutionResult(MusicAgentRoute route, boolean successful, String factualAnswer,
                                Set<AgentActionType> evidenceTypes) {
        this(route, successful, factualAnswer, evidenceTypes,
                successful ? Outcome.COMPLETE : Outcome.FAILED);
    }

    public MusicExecutionResult {
        if (route == null) throw new IllegalArgumentException("执行路由不能为空");
        factualAnswer = factualAnswer == null ? "" : factualAnswer.strip();
        evidenceTypes = evidenceTypes == null ? Set.of() : Set.copyOf(evidenceTypes);
        outcome = outcome == null ? (successful ? Outcome.COMPLETE : Outcome.FAILED) : outcome;
        if (!successful) outcome = Outcome.FAILED;
        else if (outcome == Outcome.FAILED) outcome = Outcome.COMPLETE;
    }

    public MusicExecutionResult withEvidence(Set<AgentActionType> types) {
        return new MusicExecutionResult(route, successful, factualAnswer, types, outcome);
    }

    public static MusicExecutionResult partial(MusicAgentRoute route, String factualAnswer) {
        return new MusicExecutionResult(route, true, factualAnswer, Set.of(), Outcome.PARTIAL);
    }

    public boolean partial() {
        return outcome == Outcome.PARTIAL;
    }

    public enum Outcome {
        COMPLETE,
        PARTIAL,
        FAILED
    }
}
