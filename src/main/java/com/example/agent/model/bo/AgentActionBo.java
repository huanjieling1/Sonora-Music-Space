package com.example.agent.model.bo;

import com.example.agent.agent.contract.MusicWorkflowSnapshot;

import java.util.UUID;

/** A trusted UI action produced by an Agent tool, rather than free-form model text. */
public record AgentActionBo(
        UUID id,
        AgentActionType type,
        MusicRecommendationBo recommendation,
        MusicTrackBo track,
        QqPlaylistSearchResultBo playlistSearch,
        QqArtistSearchResultBo artistSearch,
        QqChartResultBo chartResult,
        MusicProfileStoryBo profileStory,
        ProactiveSuggestionsBo proactiveSuggestions,
        MusicWorkflowSnapshot workflow
) {
    public static AgentActionBo showMusic(MusicRecommendationBo recommendation) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.SHOW_MUSIC_RESULTS, recommendation, null, null, null, null, null, null, null);
    }

    public static AgentActionBo showQqPlaylists(QqPlaylistSearchResultBo playlistSearch) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.SHOW_QQ_PLAYLIST_RESULTS, null, null, playlistSearch, null, null, null, null, null);
    }

    public static AgentActionBo showQqArtists(QqArtistSearchResultBo artistSearch) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.SHOW_QQ_ARTIST_RESULTS, null, null, null, artistSearch, null, null, null, null);
    }

    public static AgentActionBo showQqChart(QqChartResultBo chartResult) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.SHOW_QQ_CHART_RESULTS,
                null, null, null, null, chartResult, null, null, null);
    }

    public static AgentActionBo playTrack(MusicTrackBo track) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.PLAY_TRACK, null, track, null, null, null, null, null, null);
    }

    public static AgentActionBo queueMusic(MusicRecommendationBo recommendation) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.QUEUE_MUSIC_RESULTS, recommendation, null, null, null, null, null, null, null);
    }

    public static AgentActionBo showProfileStory(MusicProfileStoryBo profileStory) {
        return new AgentActionBo(
                UUID.randomUUID(), AgentActionType.SHOW_MUSIC_PROFILE_STORY,
                null, null, null, null, null, profileStory, null, null);
    }

    public static AgentActionBo showProactiveSuggestions(ProactiveSuggestionsBo suggestions) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.SHOW_PROACTIVE_SUGGESTIONS,
                null, null, null, null, null, null, suggestions, null);
    }

    public static AgentActionBo showWorkflow(MusicWorkflowSnapshot workflow) {
        return new AgentActionBo(UUID.randomUUID(), AgentActionType.SHOW_WORKFLOW_PROGRESS,
                null, null, null, null, null, null, null, workflow);
    }
}
