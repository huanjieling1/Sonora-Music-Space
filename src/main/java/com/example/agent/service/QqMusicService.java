package com.example.agent.service;

import com.example.agent.model.bo.QqMusicStatusBo;
import com.example.agent.model.bo.QqMusicPlaybackBo;
import com.example.agent.model.bo.MusicLyricsBo;
import com.example.agent.model.bo.QqPublicPlaylistBo;
import com.example.agent.model.bo.QqMusicSearchBo;
import com.example.agent.model.bo.QqMusicSearchType;
import com.example.agent.model.bo.QqArtistDetailBo;
import com.example.agent.model.bo.QqAlbumDetailBo;
import com.example.agent.model.bo.QqVideoPlaybackBo;
import com.example.agent.model.bo.QqMusicQrLoginBo;

import java.util.List;
import java.util.UUID;

public interface QqMusicService {
    QqMusicStatusBo status();

    QqMusicStatusBo saveSession(String cookie);

    QqMusicStatusBo clearSession();

    QqMusicQrLoginBo startQrLogin(long userId);

    QqMusicQrLoginBo pollQrLogin(long userId, String loginId);

    void cancelQrLogin(long userId, String loginId);

    QqMusicPlaybackBo resolvePlayback(String songMid, String mediaId);

    MusicLyricsBo lyrics(String songMid);

    List<QqPublicPlaylistBo> publicPlaylists(int page, int pageSize);

    QqMusicSearchBo search(long userId, UUID conversationId, String keyword,
                           QqMusicSearchType type, int page, int pageSize);

    QqPublicPlaylistBo publicPlaylist(long userId, UUID conversationId, String playlistId,
                                      int limit, boolean shuffle);

    QqArtistDetailBo artist(long userId, UUID conversationId, String artistMid,
                            int songPage, int songPageSize, int albumPage, int albumPageSize);

    QqAlbumDetailBo album(long userId, UUID conversationId, String albumMid);

    QqVideoPlaybackBo video(String videoId);
}
