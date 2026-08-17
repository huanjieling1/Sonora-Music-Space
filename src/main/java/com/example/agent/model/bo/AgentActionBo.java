package com.example.agent.model.bo;

import java.util.UUID;

/** A trusted UI action produced by an Agent tool, rather than free-form model text. */
public record AgentActionBo(
        UUID id,
        AgentActionType type,
        MusicRecommendationBo recommendation,
        MusicTrackBo track,
        QqPlaylistSearchResultBo playlistSearch,
        QqArtistSearchResultBo artistSearch
) {
    public static AgentActionBo showMusic(MusicRecommendationBo recommendation) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.SHOW_MUSIC_RESULTS, recommendation, null, null, null);
    }

    public static AgentActionBo showQqPlaylists(QqPlaylistSearchResultBo playlistSearch) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.SHOW_QQ_PLAYLIST_RESULTS, null, null, playlistSearch, null);
    }

    public static AgentActionBo showQqArtists(QqArtistSearchResultBo artistSearch) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.SHOW_QQ_ARTIST_RESULTS, null, null, null, artistSearch);
    }

    public static AgentActionBo playTrack(MusicTrackBo track) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.PLAY_TRACK, null, track, null, null);
    }

    public static AgentActionBo queueMusic(MusicRecommendationBo recommendation) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.QUEUE_MUSIC_RESULTS, recommendation, null, null, null);
    }
}
