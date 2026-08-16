package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicPersonalizationStatus;

import java.util.List;
import java.util.UUID;

public record MusicRecommendationVo(
        UUID searchId,
        String description,
        String searchQuery,
        String explanation,
        MusicUnderstandingVo understanding,
        List<String> providers,
        List<MusicTrackVo> tracks,
        int verifiedCount,
        int relatedCount,
        int page,
        int pageSize,
        boolean hasNext,
        int maxPages,
        String policyVersion,
        MusicPersonalizationStatus personalizationStatus
) {
    public static MusicRecommendationVo from(MusicRecommendationBo recommendation) {
        return new MusicRecommendationVo(
                recommendation.searchId(),
                recommendation.description(),
                recommendation.searchQuery(),
                recommendation.explanation(),
                MusicUnderstandingVo.from(recommendation.understanding()),
                recommendation.providers(),
                recommendation.tracks().stream().map(MusicTrackVo::from).toList(),
                recommendation.verifiedCount(),
                recommendation.relatedCount(),
                recommendation.page(),
                recommendation.pageSize(),
                recommendation.hasNext(),
                recommendation.maxPages(),
                recommendation.policyVersion(),
                recommendation.personalizationStatus());
    }
}
