package com.example.agent.model.bo;

import java.util.List;

public record MusicSearchPlaylistBo(
        String id,
        String name,
        String description,
        String coverUrl,
        String provider,
        int trackCount,
        boolean local,
        List<MusicTrackBo> tracks
) {
    public MusicSearchPlaylistBo {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }
}
