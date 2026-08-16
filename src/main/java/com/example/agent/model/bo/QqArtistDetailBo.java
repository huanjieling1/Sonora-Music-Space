package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

public record QqArtistDetailBo(
        UUID searchId,
        String mid,
        String name,
        String imageUrl,
        String foreignName,
        String birthday,
        String area,
        String description,
        String externalUrl,
        int songTotal,
        int albumTotal,
        int songPage,
        int songPageSize,
        boolean hasMoreSongs,
        int albumPage,
        int albumPageSize,
        boolean hasMoreAlbums,
        List<MusicTrackBo> tracks,
        List<Album> albums,
        String policyVersion,
        MusicPersonalizationStatus personalizationStatus
) {
    public QqArtistDetailBo {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        albums = albums == null ? List.of() : List.copyOf(albums);
    }

    public record Album(
            String mid,
            String name,
            String coverUrl,
            String publishDate,
            String type,
            int trackCount,
            String externalUrl
    ) {
    }
}
