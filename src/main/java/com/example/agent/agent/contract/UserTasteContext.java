package com.example.agent.agent.contract;

import java.util.List;

/** A read-only, evidence-carrying snapshot shared with the profile language agent. */
public record UserTasteContext(
        String stage,
        String stageLabel,
        boolean profileReady,
        long playCount,
        long uniqueTracks,
        long totalPlaybackMs,
        double completionRate,
        List<Signal> likes,
        List<Signal> avoids,
        List<Signal> labels,
        List<RankedItem> topTracks,
        List<RankedItem> topArtists,
        List<RankedItem> topTags,
        List<String> observations
) {
    public UserTasteContext {
        stage = text(stage, "EMPTY");
        stageLabel = text(stageLabel, "暂无画像");
        completionRate = Math.max(0, Math.min(1, completionRate));
        likes = copy(likes);
        avoids = copy(avoids);
        labels = copy(labels);
        topTracks = copy(topTracks);
        topArtists = copy(topArtists);
        topTags = copy(topTags);
        observations = observations == null ? List.of() : observations.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::strip).toList();
    }

    public boolean hasEvidence() {
        return playCount > 0 || !likes.isEmpty() || !avoids.isEmpty() || !labels.isEmpty();
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    public record Signal(String type, String value, String basis, double confidence, String evidenceId) {
        public Signal {
            type = text(type, "UNKNOWN");
            value = text(value, "未知");
            basis = text(basis, "暂无证据说明");
            confidence = Math.max(0, Math.min(1, confidence));
            evidenceId = text(evidenceId, type + ":" + value);
        }
    }

    public record RankedItem(String name, String detail, long count, String evidenceId) {
        public RankedItem {
            name = text(name, "未知");
            detail = detail == null ? "" : detail.strip();
            evidenceId = text(evidenceId, name);
        }
    }
}
