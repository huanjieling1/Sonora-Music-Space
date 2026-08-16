package com.example.agent.model.bo;

public record MusicTrackRelationBo(
        String trackTitle,
        String artistName,
        String albumName,
        String relationType,
        String relationLabel,
        String source,
        String sourceUrl,
        double confidence
) {
}
