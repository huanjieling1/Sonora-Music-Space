package com.example.agent.model.vo.music;

import com.example.agent.model.bo.QqMusicSearchBo;
import com.example.agent.model.bo.QqPlaylistSearchResultBo;

import java.util.List;
import java.util.UUID;

/** Allow-listed playlist-card payload returned by the Agent API. */
public record QqPlaylistSearchVo(
        UUID searchId,
        String keyword,
        String explanation,
        int page,
        int pageSize,
        long total,
        boolean hasNext,
        List<Playlist> playlists
) {
    public static QqPlaylistSearchVo from(QqPlaylistSearchResultBo result) {
        return new QqPlaylistSearchVo(
                result.searchId(), result.keyword(), result.explanation(), result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.playlists().stream().map(Playlist::from).toList());
    }

    public record Playlist(
            String id,
            String name,
            String description,
            String coverUrl,
            String creatorName,
            long listenCount,
            long trackCount,
            String externalUrl
    ) {
        private static Playlist from(QqMusicSearchBo.Playlist playlist) {
            return new Playlist(
                    playlist.id(), playlist.name(), playlist.description(), playlist.coverUrl(),
                    playlist.creatorName(), playlist.listenCount(), playlist.trackCount(), playlist.externalUrl());
        }
    }
}
