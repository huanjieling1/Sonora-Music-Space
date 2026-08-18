package com.example.agent.model.vo.music;

import java.time.LocalDateTime;
import java.util.List;

public record MusicProfileAnalyticsVo(
        long uniqueTracks,
        long playCount,
        long completeCount,
        long skipCount,
        long repeatCount,
        long totalPlaybackMs,
        double completionRate,
        boolean profileReady,
        int requiredPlayCount,
        int requiredUniqueTracks,
        List<Track> topTracks,
        List<Artist> topArtists,
        List<Tag> topTags,
        List<Label> labels,
        LocalDateTime firstPlayedAt,
        LocalDateTime lastPlayedAt,
        LocalDateTime generatedAt
) {
    public MusicProfileAnalyticsVo {
        topTracks = topTracks == null ? List.of() : List.copyOf(topTracks);
        topArtists = topArtists == null ? List.of() : List.copyOf(topArtists);
        topTags = topTags == null ? List.of() : List.copyOf(topTags);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public record Track(String trackKey, String provider, String trackId, String title,
                        String artist, String album, long playCount, long completeCount,
                        long skipCount, long repeatCount, long totalPlaybackMs,
                        LocalDateTime lastPlayedAt) {
    }

    public record Artist(String name, long uniqueTracks, long playCount, long completeCount,
                         long repeatCount, long totalPlaybackMs, double playShare,
                         LocalDateTime lastPlayedAt) {
    }

    public record Tag(String type, String value, long uniqueTracks, long playCount,
                      long totalPlaybackMs, double affinity, double confidence,
                      double playShare) {
    }

    public record Label(String code, String name, String basis, double confidence) {
    }
}
