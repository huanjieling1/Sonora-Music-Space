package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

public record MusicPlaylistDetailBo(
        MusicPlaylistBo playlist,
        UUID searchId,
        String policyVersion,
        MusicPersonalizationStatus personalizationStatus,
        List<MusicPlaylistTrackBo> tracks
) {
    public MusicPlaylistDetailBo {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }
}
