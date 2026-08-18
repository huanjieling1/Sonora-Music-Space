package com.example.agent.model.bo;

import com.example.agent.agent.contract.UserTasteContext;

import java.util.List;

/** Evidence-only payload used by the immersive Sonora profile story. */
public record MusicProfileStoryBo(
        String stage,
        String stageLabel,
        boolean profileReady,
        long playCount,
        long uniqueTracks,
        long totalPlaybackMs,
        double completionRate,
        String narrative,
        List<StoryItem> topTracks,
        List<StoryItem> topArtists,
        List<StoryItem> topTags,
        List<StorySignal> labels
) {
    public MusicProfileStoryBo {
        narrative = narrative == null ? "" : narrative.strip();
        topTracks = copy(topTracks);
        topArtists = copy(topArtists);
        topTags = copy(topTags);
        labels = copy(labels);
    }

    public static MusicProfileStoryBo from(UserTasteContext context, String narrative) {
        return new MusicProfileStoryBo(
                context.stage(), context.stageLabel(), context.profileReady(), context.playCount(),
                context.uniqueTracks(), context.totalPlaybackMs(), context.completionRate(), narrative,
                ranked(context.topTracks()), ranked(context.topArtists()), ranked(context.topTags()),
                context.labels().stream().limit(6).map(value -> new StorySignal(
                        value.value(), value.basis(), value.confidence(), value.evidenceId())).toList());
    }

    private static List<StoryItem> ranked(List<UserTasteContext.RankedItem> source) {
        long maximum = source.stream().mapToLong(UserTasteContext.RankedItem::count).max().orElse(1);
        return source.stream().limit(8).map(value -> new StoryItem(
                value.name(), value.detail(), value.count(),
                maximum <= 0 ? 0 : Math.max(0, Math.min(1, value.count() / (double) maximum)),
                value.evidenceId())).toList();
    }

    private static <T> List<T> copy(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    public record StoryItem(String name, String detail, long count, double strength, String evidenceId) {
        public StoryItem {
            name = text(name, "未知旋律");
            detail = detail == null ? "" : detail.strip();
            strength = Math.max(0, Math.min(1, strength));
            evidenceId = text(evidenceId, name);
        }
    }

    public record StorySignal(String name, String basis, double confidence, String evidenceId) {
        public StorySignal {
            name = text(name, "正在形成的偏好");
            basis = basis == null ? "" : basis.strip();
            confidence = Math.max(0, Math.min(1, confidence));
            evidenceId = text(evidenceId, name);
        }
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
