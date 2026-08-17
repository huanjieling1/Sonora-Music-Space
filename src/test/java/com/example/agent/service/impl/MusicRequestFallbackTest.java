package com.example.agent.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicRequestFallbackTest {
    @Test
    void recognizesRandomPublicPlaylistCommands() {
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist(
                "随机选择一个 QQ 音乐公开歌单并开始播放。")).isTrue();
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("随机歌单给我")).isTrue();
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("随机qq音乐歌单")).isTrue();
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("随便来个歌单")).isTrue();
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("random QQ playlist")).isTrue();
    }

    @Test
    void doesNotTreatCreatedOrOrdinaryPlaylistsAsRandomPublicPlaylists() {
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("帮我随机生成一个跑步歌单")).isFalse();
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("创建一个适合工作的歌单")).isFalse();
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("推荐一些轻松歌曲")).isFalse();
        assertThat(MusicRequestFallback.shouldPlayRandomQqPublicPlaylist("播放我收藏的歌单")).isFalse();
    }

    @Test
    void recognizesExplicitPlaylistSearchWithoutHijackingCreationOrRandomPlayback() {
        assertThat(MusicRequestFallback.shouldSearchQqPlaylists("找一个跟无畏契约相关的歌单给我")).isTrue();
        assertThat(MusicRequestFallback.shouldSearchQqPlaylists("搜索适合通勤的 QQ 音乐歌单")).isTrue();
        assertThat(MusicRequestFallback.shouldSearchQqPlaylists("recommend a Mili playlist")).isTrue();
        assertThat(MusicRequestFallback.shouldSearchQqPlaylists("随机歌单给我")).isFalse();
        assertThat(MusicRequestFallback.shouldSearchQqPlaylists("创建一个适合工作的歌单")).isFalse();
        assertThat(MusicRequestFallback.shouldSearchQqPlaylists("播放我收藏的歌单")).isFalse();
    }

    @Test
    void recognizesNamedGameMusicAndPlaybackRequests() {
        assertThat(MusicRequestFallback.shouldSearch("把Valorant的歌曲给我听")).isTrue();
        assertThat(MusicRequestFallback.wantsPlayback("把Valorant的歌曲给我听")).isTrue();
        assertThat(MusicRequestFallback.shouldSearch("播放进击的巨人原声")).isTrue();
        assertThat(MusicRequestFallback.shouldSearch("找一下英雄联盟的配乐")).isTrue();
        assertThat(MusicRequestFallback.shouldSearch("找一个跟无畏契约相关的歌单给我")).isTrue();
        assertThat(MusicRequestFallback.shouldSearch("找一些 Re:0 的歌")).isTrue();
        assertThat(MusicRequestFallback.shouldSearch("无畏契约相关歌单给我")).isTrue();
    }

    @Test
    void recognizesArtistProfileLookupsWithoutHijackingTrackOrPlaylistRequests() {
        assertThat(MusicRequestFallback.shouldSearchQqArtists("找歌手 Mili 并介绍她们")).isTrue();
        assertThat(MusicRequestFallback.shouldSearchQqArtists("介绍一下乐队 Queen 的生涯")).isTrue();
        assertThat(MusicRequestFallback.shouldSearchQqArtists("搜索艺人周杰伦的资料")).isTrue();
        assertThat(MusicRequestFallback.shouldSearchQqArtists("播放周杰伦的歌曲")).isFalse();
        assertThat(MusicRequestFallback.shouldSearchQqArtists("找 Mili 的歌")).isFalse();
        assertThat(MusicRequestFallback.shouldSearchQqArtists("找 Mili 的歌单")).isFalse();
    }

    @Test
    void doesNotHijackProfileOrUnrelatedRequests() {
        assertThat(MusicRequestFallback.shouldSearch("总结我的音乐画像")).isFalse();
        assertThat(MusicRequestFallback.shouldSearch("你有哪些能力")).isFalse();
        assertThat(MusicRequestFallback.shouldSearch("下一页")).isFalse();
    }

    @Test
    void translatesToolFailureIntoUserFacingChinese() {
        assertThat(MusicRequestFallback.failureAnswer("Music catalog request failed: QQ 音乐未连接"))
                .isEqualTo("音乐搜索失败：QQ 音乐未连接");
        assertThat(MusicRequestFallback.failureAnswer("Music catalog request failed temporarily. Ask the user to retry later."))
                .isEqualTo("音乐搜索暂时不可用，请稍后再试。");
    }
}
