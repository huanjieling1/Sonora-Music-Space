package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.QqMusicSearchType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QqMusicServiceImplTest {
    @Test
    void acceptsDedicatedBrowserLoginWithoutAInlineQrImage() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.startQrLogin()).thenReturn(new ObjectMapper().readTree("""
                {"loginId":"0ed0b6ab-c8ef-4e35-b687-41a1e46267bf","loginMode":"BROWSER",
                 "status":"WAITING_BROWSER","message":"已打开独立 Edge 登录窗口",
                 "expiresAt":"2026-08-17T15:00:00.000Z"}
                """));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var started = service.startQrLogin(7L);

        assertThat(started.loginMode()).isEqualTo("BROWSER");
        assertThat(started.qrImage()).isNull();
        assertThat(started.status()).isEqualTo("WAITING_BROWSER");
    }

    @Test
    void savesQrLoginSessionWithoutReturningCookieToTheBrowserModel() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        String loginId = "0ed0b6ab-c8ef-4e35-b687-41a1e46267bf";
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.startQrLogin()).thenReturn(new ObjectMapper().readTree("""
                {"loginId":"0ed0b6ab-c8ef-4e35-b687-41a1e46267bf","status":"WAITING_SCAN",
                 "message":"请扫码","qrImage":"data:image/png;base64,cXItYnl0ZXM=",
                 "expiresAt":"2026-08-17T15:00:00.000Z"}
                """));
        when(sidecar.pollQrLogin(loginId)).thenReturn(new ObjectMapper().readTree("""
                {"loginId":"0ed0b6ab-c8ef-4e35-b687-41a1e46267bf","status":"SUCCESS",
                 "message":"登录成功","cookie":"uin=o12345678; qm_keyst=secret"}
                """));
        when(sessions.hasSession()).thenReturn(true);
        when(sessions.maskedAccount()).thenReturn("******5678");
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var started = service.startQrLogin(7L);
        var completed = service.pollQrLogin(7L, loginId);

        assertThat(started.qrImage()).startsWith("data:image/png;base64,");
        assertThat(completed.status()).isEqualTo("SUCCESS");
        assertThat(completed.qrImage()).isNull();
        assertThat(completed.connection().sessionConfigured()).isTrue();
        verify(sessions).save("uin=o12345678; qm_keyst=secret");
    }

    @Test
    void rejectsQrLoginPollingFromAnotherApplicationUser() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        String loginId = "0ed0b6ab-c8ef-4e35-b687-41a1e46267bf";
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.startQrLogin()).thenReturn(new ObjectMapper().readTree("""
                {"loginId":"0ed0b6ab-c8ef-4e35-b687-41a1e46267bf","status":"WAITING_SCAN",
                 "message":"请扫码","qrImage":"data:image/png;base64,cXItYnl0ZXM="}
                """));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        service.startQrLogin(7L);

        assertThatThrownBy(() -> service.pollQrLogin(8L, loginId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("不属于当前用户");
    }

    @Test
    void mapsCategorizedQqSearchResultsAndPlayableTracks() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.search("acg", "TRACK", 20, 1)).thenReturn(new ObjectMapper().readTree("""
                {
                  "keyword":"acg","type":"TRACK","page":1,"pageSize":20,"total":2044,"hasNext":true,
                  "tracks":[{"songMid":"0039MnYb0qxYhV","mediaMid":"003Qui1q2u1Zho","name":"晴天",
                    "artists":["周杰伦"],"album":"叶惠美","albumMid":"000MkMni19ClKG","durationMs":269000}],
                  "artists":[],"albums":[],"playlists":[],"videos":[],"lyrics":[],"users":[]
                }
                """));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var result = service.search(7L, UUID.randomUUID(), "acg", QqMusicSearchType.TRACK, 1, 20);

        assertThat(result.total()).isEqualTo(2044);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.tracks()).singleElement().satisfies(track -> {
            assertThat(track.id()).isEqualTo("qq:0039MnYb0qxYhV");
            assertThat(track.playbackUrl()).contains("/api/music/qq/play/0039MnYb0qxYhV");
            assertThat(track.imageUrl()).contains("000MkMni19ClKG");
        });
        verify(sidecar).search("acg", "TRACK", 20, 1);
    }

    @Test
    void fallsBackFromLosslessTo320WhenResolvingPlayback() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sidecar.healthy()).thenReturn(true);
        when(sessions.cookie()).thenReturn(Optional.of("uin=o12345678; qm_keyst=value"));
        when(sidecar.play("songMid", "mediaMid", "flac", "uin=o12345678; qm_keyst=value"))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));
        when(sidecar.play("songMid", "mediaMid", "320", "uin=o12345678; qm_keyst=value"))
                .thenReturn(new ObjectMapper().readTree("{\"url\":\"https://audio.test/song.mp3\"}"));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var playback = service.resolvePlayback("songMid", "mediaMid");
        assertThat(playback.url().toString()).isEqualTo("https://audio.test/song.mp3");
        assertThat(playback.quality()).isEqualTo("320");
        verify(sidecar).play("songMid", "mediaMid", "flac", "uin=o12345678; qm_keyst=value");
        verify(sidecar).play("songMid", "mediaMid", "320", "uin=o12345678; qm_keyst=value");
    }

    @Test
    void parsesAndMergesSynchronizedLyrics() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.lyrics("003rJSwm3TechU")).thenReturn(new ObjectMapper().readTree("""
                {
                  "lyric": "[ti:Test]\\n[00:01.25]First line\\n[00:04.000]Second &amp; line",
                  "translation": "[00:01.25]第一句\\n[00:04.00]第二句",
                  "romanization": "[00:01.25]Dai ichi"
                }
                """));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var lyrics = service.lyrics("003rJSwm3TechU");

        assertThat(lyrics.available()).isTrue();
        assertThat(lyrics.synced()).isTrue();
        assertThat(lyrics.lines()).hasSize(2);
        assertThat(lyrics.lines().get(0).timeMs()).isEqualTo(1_250L);
        assertThat(lyrics.lines().get(0).translation()).isEqualTo("第一句");
        assertThat(lyrics.lines().get(0).romanization()).isEqualTo("Dai ichi");
        assertThat(lyrics.lines().get(1).text()).isEqualTo("Second & line");
    }

    @Test
    void loadsRealPublicPlaylistMetadataAndTracks() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.playlist("7707261125", 60)).thenReturn(new ObjectMapper().readTree("""
                {
                  "id": "7707261125",
                  "name": "甜度爆表",
                  "description": "公开歌单",
                  "coverUrl": "https://qpic.y.qq.com/cover.jpg",
                  "creatorName": "我想要两颗西柚",
                  "listenCount": 8545075,
                  "trackCount": 66,
                  "tags": ["流行", "甜蜜"],
                  "tracks": [{
                    "songMid": "002xTzGb2UBQRk",
                    "mediaMid": "002AkhKv0YDLIl",
                    "name": "你的",
                    "artists": ["DouDou"],
                    "album": "你的",
                    "albumMid": "0023VbHy1oT80v",
                    "durationMs": 163000
                  }]
                }
                """));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var playlist = service.publicPlaylist(7L, UUID.randomUUID(), "7707261125", 60, false);

        assertThat(playlist.name()).isEqualTo("甜度爆表");
        assertThat(playlist.creatorName()).isEqualTo("我想要两颗西柚");
        assertThat(playlist.tracks()).hasSize(1);
        assertThat(playlist.tracks().get(0).id()).isEqualTo("qq:002xTzGb2UBQRk");
        assertThat(playlist.tracks().get(0).playbackUrl())
                .contains("/api/music/qq/play/002xTzGb2UBQRk").contains("mediaId=002AkhKv0YDLIl");
        assertThat(playlist.searchId()).isNotNull();
    }

    @Test
    void listsPublicPlaylistsWithoutRequiringQqLoginSession() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.publicPlaylists(1, 12)).thenReturn(new ObjectMapper().readTree("""
                {"playlists":[{"id":"7578943835","name":"丧系 Rap","creatorName":"离妄.",
                "coverUrl":"https://qpic.y.qq.com/cover.jpg","listenCount":1812595}]}
                """));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var playlists = service.publicPlaylists(1, 12);

        assertThat(playlists).hasSize(1);
        assertThat(playlists.get(0).creatorName()).isEqualTo("离妄.");
        verify(sidecar).publicPlaylists(1, 12);
    }

    @Test
    void loadsArtistCareerSongsAndAlbumsBySingerMid() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.artist("001z2JmX09LLgL", 1, 20, 1, 12)).thenReturn(new ObjectMapper().readTree("""
                {
                  "mid":"001z2JmX09LLgL","name":"汪苏泷","foreignName":"Silence Wang",
                  "area":"中国大陆","birthday":"1989-09-17","description":"歌手资料",
                  "imageUrl":"https://y.gtimg.cn/singer.jpg","songTotal":943,"albumTotal":118,
                  "songPage":1,"songPageSize":20,"hasMoreSongs":true,
                  "albumPage":1,"albumPageSize":12,"hasMoreAlbums":true,
                  "tracks":[{"songMid":"004gZy7l1GYJ1Q","mediaMid":"004gZy7l1GYJ1Q","name":"后会无期",
                    "artists":["汪苏泷"],"album":"不良少年","albumMid":"001album","durationMs":211000}],
                  "albums":[{"mid":"004NXLBx4K0r87","name":"好安静","publishDate":"2011-09-09",
                    "type":"录音室专辑","trackCount":10,"coverUrl":"https://y.gtimg.cn/album.jpg"}]
                }
                """));
        var service = new QqMusicServiceImpl(properties(), sidecar, sessions);

        var artist = service.artist(7L, UUID.randomUUID(), "001z2JmX09LLgL", 1, 20, 1, 12);

        assertThat(artist.name()).isEqualTo("汪苏泷");
        assertThat(artist.foreignName()).isEqualTo("Silence Wang");
        assertThat(artist.songTotal()).isEqualTo(943);
        assertThat(artist.tracks()).singleElement().satisfies(track -> {
            assertThat(track.id()).isEqualTo("qq:004gZy7l1GYJ1Q");
            assertThat(track.reasonCodes()).contains("QQ_ARTIST_PAGE");
        });
        assertThat(artist.albums()).singleElement().satisfies(album ->
                assertThat(album.name()).isEqualTo("好安静"));
        verify(sidecar).artist("001z2JmX09LLgL", 1, 20, 1, 12);
    }

    private static MusicCatalogProperties properties() {
        return new MusicCatalogProperties(5,
                new MusicCatalogProperties.Jamendo("", "https://jamendo.test"),
                new MusicCatalogProperties.Audius("", "https://audius.test"),
                new MusicCatalogProperties.Youtube("", "https://youtube.test"),
                new MusicCatalogProperties.Qq(true, "http://qq.test", "runtime-data", "flac"));
    }
}
