package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

/** A QQ Music playlist search result rendered as trusted Agent UI cards. */
public record QqPlaylistSearchResultBo(
        UUID searchId,
        String keyword,
        String explanation,
        int page,
        int pageSize,
        long total,
        boolean hasNext,
        List<QqMusicSearchBo.Playlist> playlists
) {
    public QqPlaylistSearchResultBo {
        playlists = playlists == null ? List.of() : List.copyOf(playlists);
    }

    public QqPlaylistSearchResultBo(UUID searchId, String keyword, int page, int pageSize,
                                    long total, boolean hasNext,
                                    List<QqMusicSearchBo.Playlist> playlists) {
        this(searchId, keyword, "", page, pageSize, total, hasNext, playlists);
    }

    public static QqPlaylistSearchResultBo from(QqMusicSearchBo result) {
        return from(result, "");
    }

    public static QqPlaylistSearchResultBo from(QqMusicSearchBo result, String explanation) {
        return new QqPlaylistSearchResultBo(
                result.searchId(), result.keyword(), explanation, result.page(), result.pageSize(),
                result.total(), result.hasNext(), result.playlists());
    }
}
