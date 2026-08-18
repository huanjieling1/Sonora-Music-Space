package com.example.agent.agent.contract;

import java.util.List;

/** Validated compound intent produced before any feedback, profile or catalog mutation. */
public record MusicTurnPlan(
        boolean actionable,
        boolean latestRecommendationReferenced,
        boolean rejectLatestBatch,
        List<MusicPreferenceChange> preferences,
        boolean recommendAgain,
        String recommendationRequest,
        boolean refreshBatch,
        double confidence,
        String clarificationQuestion
) {
    public MusicTurnPlan(boolean actionable, boolean latestRecommendationReferenced,
                         boolean rejectLatestBatch, List<MusicPreferenceChange> preferences,
                         boolean recommendAgain, String recommendationRequest,
                         double confidence, String clarificationQuestion) {
        this(actionable, latestRecommendationReferenced, rejectLatestBatch, preferences,
                recommendAgain, recommendationRequest, false, confidence, clarificationQuestion);
    }

    public MusicTurnPlan {
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        recommendationRequest = text(recommendationRequest);
        clarificationQuestion = text(clarificationQuestion);
        confidence = Double.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0;
    }

    public static MusicTurnPlan none() {
        return new MusicTurnPlan(false, false, false, List.of(), false, "", false, 0, "");
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
