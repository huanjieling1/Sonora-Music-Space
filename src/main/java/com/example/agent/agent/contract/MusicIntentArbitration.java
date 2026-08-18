package com.example.agent.agent.contract;

/** Auditable result of reconciling model intent, literal evidence and support context. */
public record MusicIntentArbitration(
        MusicAgentRoute route,
        MusicIntentUnderstanding understanding,
        String reason,
        MusicIntentEvidence evidence
) {
    public MusicIntentArbitration {
        route = route == null ? MusicAgentRoute.CONVERSATION : route;
        understanding = understanding == null
                ? MusicIntentUnderstanding.routed(route, MusicIntentDraft.unknown()) : understanding;
        reason = reason == null ? "" : reason.strip();
    }
}
