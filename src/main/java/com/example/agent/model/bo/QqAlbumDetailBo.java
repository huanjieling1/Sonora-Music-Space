package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

public record QqAlbumDetailBo(
        UUID searchId,
        String mid,
        String name,
        String coverUrl,
        List<String> artists,
        String artistMid,
        String publishDate,
        String genre,
        String language,
        String company,
        String description,
        int trackCount,
        String externalUrl,
        List<MusicTrackBo> tracks,
        String policyVersion,
        MusicPersonalizationStatus personalizationStatus
) {
    public QqAlbumDetailBo {
        artists = artists == null ? List.of() : List.copyOf(artists);
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }
}
