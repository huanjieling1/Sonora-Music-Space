package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

public record MusicRecommendationBo(
        UUID searchId,
        String description,
        String searchQuery,
        String explanation,
        MusicUnderstandingBo understanding,
        List<String> providers,
        List<MusicTrackBo> tracks,
        int verifiedCount,
        int relatedCount,
        int page,
        int pageSize,
        boolean hasNext,
        int maxPages,
        String policyVersion,
        MusicPersonalizationStatus personalizationStatus
) {
    public MusicRecommendationBo(UUID searchId, String description, String searchQuery, String explanation,
                                 MusicUnderstandingBo understanding, List<String> providers,
                                 List<MusicTrackBo> tracks, int verifiedCount, int relatedCount,
                                 int page, int pageSize, boolean hasNext, int maxPages) {
        this(searchId, description, searchQuery, explanation, understanding, providers, tracks,
                verifiedCount, relatedCount, page, pageSize, hasNext, maxPages,
                "baseline-v1", MusicPersonalizationStatus.DISABLED);
    }

    public MusicRecommendationBo(String description, String searchQuery, String explanation,
                                 List<String> providers, List<MusicTrackBo> tracks) {
        this(UUID.randomUUID(), description, searchQuery, explanation, MusicUnderstandingBo.unresolved(),
                providers, tracks, 0, tracks == null ? 0 : tracks.size(), 1,
                Math.max(1, Math.min(10, tracks == null ? 10 : tracks.size())), false, 20,
                "baseline-v1", MusicPersonalizationStatus.DISABLED);
    }

    public MusicRecommendationBo(String description, String searchQuery, String explanation,
                                 List<String> providers, List<MusicTrackBo> tracks,
                                 int page, int pageSize, boolean hasNext, int maxPages) {
        this(UUID.randomUUID(), description, searchQuery, explanation, MusicUnderstandingBo.unresolved(),
                providers, tracks, 0, tracks == null ? 0 : tracks.size(), page, pageSize, hasNext, maxPages,
                "baseline-v1", MusicPersonalizationStatus.DISABLED);
    }
}
