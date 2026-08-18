package com.example.agent.agent.contract;

/** Validated semantic understanding used by routing, planning and result evaluation. */
public record MusicIntentUnderstanding(
        MusicAgentRoute route,
        MusicIntentDraft intent,
        boolean supported,
        String userMessage
) {
    public MusicIntentUnderstanding {
        route = route == null ? MusicAgentRoute.CONVERSATION : route;
        intent = intent == null ? MusicIntentDraft.unknown() : intent;
        userMessage = userMessage == null ? "" : userMessage.strip();
    }

    public static MusicIntentUnderstanding routed(MusicAgentRoute route, MusicIntentDraft intent) {
        return new MusicIntentUnderstanding(route, intent, true, "");
    }

    public static MusicIntentUnderstanding clarify(MusicIntentDraft intent, String question) {
        return new MusicIntentUnderstanding(MusicAgentRoute.SCOPE_CLARIFICATION, intent, true, question);
    }

    public static MusicIntentUnderstanding unsupported(MusicIntentDraft intent, String message) {
        return new MusicIntentUnderstanding(MusicAgentRoute.SCOPE_CLARIFICATION, intent, false, message);
    }
}
