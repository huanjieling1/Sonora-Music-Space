package com.example.agent.model.vo.agent;

import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.vo.music.MusicRecommendationVo;
import com.example.agent.model.vo.music.MusicTrackVo;
import com.example.agent.model.vo.music.QqPlaylistSearchVo;
import com.example.agent.model.vo.music.QqArtistSearchVo;

import java.util.UUID;

/** Serializable, allow-listed action contract consumed by the Sonora workspace. */
public record AgentActionVo(
        UUID id,
        String type,
        MusicRecommendationVo recommendation,
        MusicTrackVo track,
        QqPlaylistSearchVo playlistSearch,
        QqArtistSearchVo artistSearch,
        com.example.agent.model.bo.QqChartResultBo chartResult,
        MusicProfileStoryVo profileStory,
        com.example.agent.model.bo.ProactiveSuggestionsBo proactiveSuggestions,
        MusicWorkflowProgressVo workflow
) {
    public static AgentActionVo from(AgentActionBo action) {
        return new AgentActionVo(
                action.id(),
                action.type().name(),
                action.recommendation() == null ? null : MusicRecommendationVo.from(action.recommendation()),
                action.track() == null ? null : MusicTrackVo.from(action.track()),
                action.playlistSearch() == null ? null : QqPlaylistSearchVo.from(action.playlistSearch()),
                action.artistSearch() == null ? null : QqArtistSearchVo.from(action.artistSearch()),
                action.chartResult(),
                action.profileStory() == null ? null : MusicProfileStoryVo.from(action.profileStory()),
                action.proactiveSuggestions(),
                action.workflow() == null ? null : MusicWorkflowProgressVo.from(action.workflow()));
    }
}
