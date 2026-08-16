package com.example.agent.model.bo;

import java.util.UUID;

/** A trusted UI action produced by an Agent tool, rather than free-form model text. */
public record AgentActionBo(
        UUID id,
        AgentActionType type,
        MusicRecommendationBo recommendation,
        MusicTrackBo track
) {
    public static AgentActionBo showMusic(MusicRecommendationBo recommendation) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.SHOW_MUSIC_RESULTS, recommendation, null);
    }

    public static AgentActionBo playTrack(MusicTrackBo track) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.PLAY_TRACK, null, track);
    }

    public static AgentActionBo queueMusic(MusicRecommendationBo recommendation) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.QUEUE_MUSIC_RESULTS, recommendation, null);
    }
}
