package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicCatalogSearchBo;
import com.example.agent.model.bo.MusicCatalogSearchType;

import java.util.List;
import java.util.UUID;

public record MusicCatalogSearchVo(
        UUID searchId,
        String keyword,
        MusicCatalogSearchType type,
        List<MusicTrackVo> tracks,
        List<MusicSearchArtistVo> artists,
        List<MusicSearchGenreVo> genres,
        List<MusicSearchPlaylistVo> playlists,
        int page,
        int pageSize,
        boolean hasNext,
        List<String> providers
) {
    public static MusicCatalogSearchVo from(MusicCatalogSearchBo search) {
        return new MusicCatalogSearchVo(search.searchId(), search.keyword(), search.type(),
                search.tracks().stream().map(MusicTrackVo::from).toList(),
                search.artists().stream().map(MusicSearchArtistVo::from).toList(),
                search.genres().stream().map(MusicSearchGenreVo::from).toList(),
                search.playlists().stream().map(MusicSearchPlaylistVo::from).toList(),
                search.page(), search.pageSize(), search.hasNext(), search.providers());
    }
}
