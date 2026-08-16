package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicMatchType;

import java.util.List;

public record MusicTrackVo(
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
        boolean exploration
) {
    public static MusicTrackVo from(MusicTrackBo track) {
        return new MusicTrackVo(track.id(), track.name(), track.artists(), track.album(),
                track.imageUrl(), track.durationMs(), track.externalUrl(), track.provider(),
                track.playbackType(), track.playbackUrl(), track.licenseUrl(), track.matchType(),
                track.relationLabel(), track.relevanceScore(), track.reasonCodes(),
                track.reasonText(), track.exploration());
    }
}
