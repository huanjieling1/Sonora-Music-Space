package com.example.agent.model.vo.agent;

import com.example.agent.model.bo.MusicProfileStoryBo;

import java.util.List;

public record MusicProfileStoryVo(
        String stage,
        String stageLabel,
        boolean profileReady,
        long playCount,
        long uniqueTracks,
        long totalPlaybackMs,
        double completionRate,
        String narrative,
        List<StoryItemVo> topTracks,
        List<StoryItemVo> topArtists,
        List<StoryItemVo> topTags,
        List<StorySignalVo> labels
) {
    public static MusicProfileStoryVo from(MusicProfileStoryBo story) {
        return new MusicProfileStoryVo(
                story.stage(), story.stageLabel(), story.profileReady(), story.playCount(), story.uniqueTracks(),
                story.totalPlaybackMs(), story.completionRate(), story.narrative(),
                story.topTracks().stream().map(StoryItemVo::from).toList(),
                story.topArtists().stream().map(StoryItemVo::from).toList(),
                story.topTags().stream().map(StoryItemVo::from).toList(),
                story.labels().stream().map(StorySignalVo::from).toList());
    }

    public record StoryItemVo(String name, String detail, long count, double strength) {
        static StoryItemVo from(MusicProfileStoryBo.StoryItem value) {
            return new StoryItemVo(value.name(), value.detail(), value.count(), value.strength());
        }
    }

    public record StorySignalVo(String name, String basis, double confidence) {
        static StorySignalVo from(MusicProfileStoryBo.StorySignal value) {
            return new StorySignalVo(value.name(), value.basis(), value.confidence());
        }
    }
}
