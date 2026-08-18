package com.example.agent.agent.contract;

import java.util.ArrayList;
import java.util.List;

/** Explicit per-turn state; internal agents never share persistent chat memory. */
public record MusicAgentWorkflowState(
        MusicAgentTurn turn,
        MusicAgentRoute route,
        MusicIntentUnderstanding understanding,
        UserTasteContext tasteContext,
        MusicExecutionResult executionResult,
        String answer,
        List<String> participants,
        MusicWorkflowSnapshot workflow,
        MusicSupportContext supportContext,
        MusicSupportSuggestionPlan supportPlan
) {
    public MusicAgentWorkflowState {
        participants = participants == null ? List.of() : List.copyOf(participants);
        supportContext = supportContext == null ? MusicSupportContext.none() : supportContext;
    }

    public static MusicAgentWorkflowState start(MusicAgentTurn turn, MusicAgentRoute route) {
        return new MusicAgentWorkflowState(turn, route, null, null, null, null, List.of("intent"), null,
                MusicSupportContext.none(), null);
    }

    public static MusicAgentWorkflowState start(MusicAgentTurn turn, MusicAgentRoute route,
                                                String initialParticipant) {
        return new MusicAgentWorkflowState(turn, route, null, null, null, null,
                initialParticipant == null || initialParticipant.isBlank()
                        ? List.of() : List.of(initialParticipant.strip()), null, MusicSupportContext.none(), null);
    }

    public static MusicAgentWorkflowState start(MusicAgentTurn turn, MusicIntentUnderstanding understanding,
                                                String initialParticipant) {
        MusicIntentUnderstanding safe = understanding == null
                ? MusicIntentUnderstanding.routed(MusicAgentRoute.CONVERSATION, MusicIntentDraft.unknown())
                : understanding;
        return new MusicAgentWorkflowState(turn, safe.route(), safe, null, null, null,
                initialParticipant == null || initialParticipant.isBlank()
                        ? List.of() : List.of(initialParticipant.strip()), null, MusicSupportContext.none(), null);
    }

    public MusicAgentWorkflowState withProfile(ProfileAgentResult profile) {
        return new MusicAgentWorkflowState(turn, route, understanding, profile.context(), executionResult,
                profile.answer(), append("profile"), workflow, supportContext, supportPlan);
    }

    public MusicAgentWorkflowState withTasteContext(UserTasteContext context) {
        return new MusicAgentWorkflowState(turn, route, understanding, context, executionResult,
                answer, append("profile-context"), workflow, supportContext, supportPlan);
    }

    public MusicAgentWorkflowState withExecution(MusicExecutionResult execution) {
        return new MusicAgentWorkflowState(turn, route, understanding, tasteContext, execution,
                answer, append("execution"), workflow, supportContext, supportPlan);
    }

    public MusicAgentWorkflowState participated(String participant) {
        return new MusicAgentWorkflowState(turn, route, understanding, tasteContext, executionResult,
                answer, append(participant), workflow, supportContext, supportPlan);
    }

    public MusicAgentWorkflowState completed(String value, String participant) {
        return new MusicAgentWorkflowState(turn, route, understanding, tasteContext, executionResult,
                value, append(participant), workflow, supportContext, supportPlan);
    }

    public MusicAgentWorkflowState withWorkflow(MusicWorkflowSnapshot snapshot) {
        return new MusicAgentWorkflowState(turn, route, understanding, tasteContext, executionResult,
                answer, participants, snapshot, supportContext, supportPlan);
    }

    public MusicAgentWorkflowState withSupport(MusicSupportContext context,
                                               MusicSupportSuggestionPlan plan) {
        return new MusicAgentWorkflowState(turn, route, understanding, tasteContext, executionResult,
                answer, append("support-context"), workflow,
                context == null ? MusicSupportContext.none() : context, plan);
    }

    private List<String> append(String participant) {
        ArrayList<String> result = new ArrayList<>(participants);
        if (participant != null && !participant.isBlank() && !result.contains(participant)) {
            result.add(participant);
        }
        return List.copyOf(result);
    }
}
