package com.example.agent.model.vo.music;

import com.example.agent.model.bo.QqArtistDetailBo;
import com.example.agent.model.bo.QqArtistSearchResultBo;

import java.util.List;
import java.util.UUID;

/** Allow-listed artist-card payload returned by the Agent API. */
public record QqArtistSearchVo(
        UUID searchId,
        String keyword,
        int page,
        int pageSize,
        long total,
        boolean hasNext,
        List<Artist> artists
) {
    public static QqArtistSearchVo from(QqArtistSearchResultBo result) {
        return new QqArtistSearchVo(
                result.searchId(), result.keyword(), result.page(), result.pageSize(), result.total(),
                result.hasNext(), result.artists().stream().map(Artist::from).toList());
    }

    public record Artist(
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
            List<MusicTrackVo> tracks,
            List<Album> albums,
            String biographySummary,
            String achievementSummary,
            String styleSummary
    ) {
        private static Artist from(QqArtistSearchResultBo.ArtistProfile artist) {
            return new Artist(
                    artist.mid(), artist.name(), artist.imageUrl(), artist.foreignName(), artist.birthday(),
                    artist.area(), artist.description(), artist.externalUrl(), artist.songTotal(), artist.albumTotal(),
                    artist.videoTotal(), artist.hasMoreSongs(), artist.hasMoreAlbums(),
                    artist.tracks().stream().map(MusicTrackVo::from).toList(),
                    artist.albums().stream().map(Album::from).toList(), artist.biographySummary(),
                    artist.achievementSummary(), artist.styleSummary());
        }
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
        private static Album from(QqArtistDetailBo.Album album) {
            return new Album(album.mid(), album.name(), album.coverUrl(), album.publishDate(), album.type(),
                    album.trackCount(), album.externalUrl());
        }
    }
}
