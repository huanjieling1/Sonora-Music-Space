package com.example.agent.service;

import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.ao.PreparedMusicRecommendationAo;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicStatusBo;

public interface MusicRecommendationService {
    MusicStatusBo status();

    MusicRecommendationBo recommend(MusicRecommendationAo command);

    MusicRecommendationBo recommendPrepared(PreparedMusicRecommendationAo command);
}
