package com.example.agent.agent.contract;

/** Structured, transient understanding of a conversational support need. */
public record MusicSupportContext(
        InteractionType interactionType,
        EmotionalSignal signal,
        SupportGoal goal,
        double confidence,
        String musicDirection
) {
    public MusicSupportContext {
        interactionType = interactionType == null ? InteractionType.NONE : interactionType;
        signal = signal == null ? EmotionalSignal.NONE : signal;
        goal = goal == null ? SupportGoal.NONE : goal;
        confidence = Double.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0;
        String normalizedDirection = musicDirection == null ? "" : musicDirection.strip();
        musicDirection = normalizedDirection.isEmpty() ? ""
                : normalizedDirection.substring(0, Math.min(120, normalizedDirection.length()));
    }

    public static MusicSupportContext none() {
        return new MusicSupportContext(InteractionType.NONE, EmotionalSignal.NONE,
                SupportGoal.NONE, 0, "");
    }

    public boolean actionable() {
        return interactionType == InteractionType.SUPPORT_SEEKING && goal != SupportGoal.NONE
                && confidence >= 0.62;
    }

    public boolean safetyConcern() {
        return interactionType == InteractionType.SAFETY_CONCERN;
    }

    public enum InteractionType {
        SUPPORT_SEEKING,
        CASUAL_CONVERSATION,
        SAFETY_CONCERN,
        NONE
    }

    public enum EmotionalSignal {
        SADNESS,
        LONELINESS,
        STRESS,
        ANXIETY,
        FATIGUE,
        SLEEPLESSNESS,
        LOW_ENERGY,
        CELEBRATION,
        NONE
    }

    public enum SupportGoal {
        SOOTHE,
        ACCOMPANY,
        ENERGIZE,
        DISTRACT,
        FOCUS,
        EXPLORE,
        SAFETY,
        NONE
    }
}
