package com.example.agent.model.bo;

import java.util.List;

public record MusicTrackBo(
        String id,
        String name,
        List<String> artists,
        String album,
        String imageUrl,
        long durationMs,
        String externalUrl,
        String provider,
        String playbackType,
        String playbackUrl,
        String licenseUrl,
        MusicMatchType matchType,
        String relationLabel,
        double relevanceScore,
        List<String> reasonCodes,
        String reasonText,
        boolean exploration,
        String albumId
) {
    public MusicTrackBo {
        artists = artists == null ? List.of() : List.copyOf(artists);
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public MusicTrackBo(String id, String name, List<String> artists, String album, String imageUrl,
                        long durationMs, String externalUrl, String provider, String playbackType,
                        String playbackUrl, String licenseUrl) {
        this(id, name, artists, album, imageUrl, durationMs, externalUrl, provider, playbackType,
                playbackUrl, licenseUrl, null, null, 0, List.of(), null, false, null);
    }

    public MusicTrackBo(String id, String name, List<String> artists, String album, String imageUrl,
                        long durationMs, String externalUrl, String provider, String playbackType,
                        String playbackUrl, String licenseUrl, MusicMatchType matchType,
                        String relationLabel, double relevanceScore) {
        this(id, name, artists, album, imageUrl, durationMs, externalUrl, provider, playbackType,
                playbackUrl, licenseUrl, matchType, relationLabel, relevanceScore, List.of(), null, false);
    }

    public MusicTrackBo(String id, String name, List<String> artists, String album, String imageUrl,
                        long durationMs, String externalUrl, String provider, String playbackType,
                        String playbackUrl, String licenseUrl, MusicMatchType matchType,
                        String relationLabel, double relevanceScore, List<String> reasonCodes,
                        String reasonText, boolean exploration) {
        this(id, name, artists, album, imageUrl, durationMs, externalUrl, provider, playbackType,
                playbackUrl, licenseUrl, matchType, relationLabel, relevanceScore, reasonCodes,
                reasonText, exploration, null);
    }

    public MusicTrackBo withAlbumId(String value) {
        return new MusicTrackBo(id, name, artists, album, imageUrl, durationMs, externalUrl, provider,
                playbackType, playbackUrl, licenseUrl, matchType, relationLabel, relevanceScore,
                reasonCodes, reasonText, exploration, value);
    }

    public MusicTrackBo withMatch(MusicMatchType type, String label, double score) {
        return new MusicTrackBo(id, name, artists, album, imageUrl, durationMs, externalUrl, provider,
                playbackType, playbackUrl, licenseUrl, type, label, score,
                reasonCodes, reasonText, exploration, albumId);
    }

    public MusicTrackBo withRecommendationReason(List<String> codes, String text, boolean isExploration,
                                                  double score) {
        return new MusicTrackBo(id, name, artists, album, imageUrl, durationMs, externalUrl, provider,
                playbackType, playbackUrl, licenseUrl, matchType, relationLabel, score,
                codes, text, isExploration, albumId);
    }
}
