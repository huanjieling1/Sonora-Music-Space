package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicSearchPlaylistBo;

import java.util.List;

public record MusicSearchPlaylistVo(
        String id,
        String name,
        String description,
        String coverUrl,
        String provider,
        int trackCount,
        boolean local,
        List<MusicTrackVo> tracks
) {
    public static MusicSearchPlaylistVo from(MusicSearchPlaylistBo playlist) {
        return new MusicSearchPlaylistVo(playlist.id(), playlist.name(), playlist.description(),
                playlist.coverUrl(), playlist.provider(), playlist.trackCount(), playlist.local(),
                playlist.tracks().stream().map(MusicTrackVo::from).toList());
    }
}
