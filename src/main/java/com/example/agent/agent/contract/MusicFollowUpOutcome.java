package com.example.agent.agent.contract;

/** Effects accepted from a contextual follow-up before an optional catalog rerun. */
public record MusicFollowUpOutcome(
        boolean recommendAgain,
        String recommendationRequest,
        boolean refreshBatch,
        String acknowledgment,
        int rejectedTrackCount
) {
    public MusicFollowUpOutcome(boolean recommendAgain, String recommendationRequest,
                                String acknowledgment, int rejectedTrackCount) {
        this(recommendAgain, recommendationRequest, false, acknowledgment, rejectedTrackCount);
    }

    public MusicFollowUpOutcome {
        recommendationRequest = recommendationRequest == null ? "" : recommendationRequest.strip();
        acknowledgment = acknowledgment == null ? "" : acknowledgment.strip();
        rejectedTrackCount = Math.max(0, rejectedTrackCount);
    }
}
