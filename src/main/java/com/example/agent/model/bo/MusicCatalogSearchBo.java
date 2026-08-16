package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

public record MusicCatalogSearchBo(
        UUID searchId,
        String keyword,
        MusicCatalogSearchType type,
        List<MusicTrackBo> tracks,
        List<MusicSearchArtistBo> artists,
        List<MusicSearchGenreBo> genres,
        List<MusicSearchPlaylistBo> playlists,
        int page,
        int pageSize,
        boolean hasNext,
        List<String> providers
) {
    public MusicCatalogSearchBo {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        artists = artists == null ? List.of() : List.copyOf(artists);
        genres = genres == null ? List.of() : List.copyOf(genres);
        playlists = playlists == null ? List.of() : List.copyOf(playlists);
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
