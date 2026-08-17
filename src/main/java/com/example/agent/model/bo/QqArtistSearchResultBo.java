package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

/** QQ Music artist profiles prepared for the Agent's large, trusted artist cards. */
public record QqArtistSearchResultBo(
        UUID searchId,
        String keyword,
        int page,
        int pageSize,
        long total,
        boolean hasNext,
        List<ArtistProfile> artists
) {
    public QqArtistSearchResultBo {
        artists = artists == null ? List.of() : List.copyOf(artists);
    }

    public record ArtistProfile(
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
            long videoTotal,
            boolean hasMoreSongs,
            boolean hasMoreAlbums,
            List<MusicTrackBo> tracks,
            List<QqArtistDetailBo.Album> albums,
            String biographySummary,
            String achievementSummary,
            String styleSummary
    ) {
        public ArtistProfile {
            tracks = tracks == null ? List.of() : List.copyOf(tracks);
            albums = albums == null ? List.of() : List.copyOf(albums);
        }
    }
}
