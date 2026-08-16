package com.example.agent.model.vo.agent;

import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.vo.music.MusicRecommendationVo;
import com.example.agent.model.vo.music.MusicTrackVo;

import java.util.UUID;

/** Serializable, allow-listed action contract consumed by the Sonora workspace. */
public record AgentActionVo(
        UUID id,
        String type,
        MusicRecommendationVo recommendation,
        MusicTrackVo track
) {
    public static AgentActionVo from(AgentActionBo action) {
        return new AgentActionVo(
                action.id(),
                action.type().name(),
                action.recommendation() == null ? null : MusicRecommendationVo.from(action.recommendation()),
                action.track() == null ? null : MusicTrackVo.from(action.track()));
    }
}
