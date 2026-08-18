package com.example.agent.tools;

import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.exception.AppException;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.ao.PreparedMusicRecommendationAo;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.QqMusicSearchBo;
import com.example.agent.model.bo.QqMusicSearchType;
import com.example.agent.model.bo.QqMusicPlaybackBo;
import com.example.agent.model.bo.QqPublicPlaylistBo;
import com.example.agent.model.bo.QqArtistDetailBo;
import com.example.agent.service.MusicKeywordExtractor;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.QqMusicService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;

class MusicAgentToolsTest {
    private final MusicRecommendationService recommendationService = mock(MusicRecommendationService.class);
    private final MusicPersonalizationService personalizationService = mock(MusicPersonalizationService.class);
    private final MusicAgentSessionStore sessionStore = new MusicAgentSessionStore();
    private final AgentActionContext actionContext = new AgentActionContext();
    private final MusicAgentTools tools = new MusicAgentTools(
            recommendationService, personalizationService, sessionStore, actionContext);
    private final ConversationMemoryId memoryId = new ConversationMemoryId(
            42L, UUID.fromString("44444444-4444-4444-8444-444444444444"));

    @BeforeEach
    void beginAgentRequest() {
        actionContext.begin(memoryId);
    }

    @AfterEach
    void clearAgentRequest() {
        actionContext.clear();
    }

    @Test
    void recommendationCreatesVisibleActionAndSupportsPlaybackFollowUp() {
        MusicTrackBo track = track("qq:1", "Iron Lotus");
        MusicRecommendationBo recommendation = new MusicRecommendationBo(
                "热血战斗音乐", "energetic battle music", "找到 1 首歌曲", List.of("qq"), List.of(track));
        when(recommendationService.recommend(new MusicRecommendationAo(
                42L, memoryId.conversationId(), "热血战斗音乐", 1, 10)))
                .thenReturn(recommendation);

        String searchResult = tools.recommendMusic("热血战斗音乐");
        String playResult = tools.playRecommendedTrack(1);

        assertThat(searchResult).contains("Iron Lotus");
        assertThat(playResult).contains("Iron Lotus");
        assertThat(actionContext.actions()).extracting(action -> action.type())
                .containsExactly(AgentActionType.SHOW_MUSIC_RESULTS, AgentActionType.PLAY_TRACK);
        assertThat(actionContext.actions().get(0).recommendation()).isEqualTo(recommendation);
        assertThat(actionContext.actions().get(1).track()).isEqualTo(track);
    }

    @Test
    void agentSearchSubmitsOneKeywordToQqAndKeepsQqOrder() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicKeywordExtractor keywordExtractor = mock(MusicKeywordExtractor.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService, keywordExtractor);
        MusicTrackBo first = track("qq:re0:1", "STYX HELIX");
        MusicTrackBo second = track("qq:re0:2", "Stay Alive");
        when(keywordExtractor.extract("找一些Re0的歌")).thenReturn(new MusicKeywordExtractor.ExtractedKeyword(
                "Re0", com.example.agent.model.bo.MusicSearchIntent.ENTITY_RELATED,
                com.example.agent.model.bo.MusicUnderstandingBo.unresolved()));
        when(qqMusicService.search(42L, memoryId.conversationId(), "Re0",
                QqMusicSearchType.TRACK, 1, 10)).thenReturn(new QqMusicSearchBo(
                UUID.fromString("55555555-5555-4555-8555-555555555555"), "Re0",
                QqMusicSearchType.TRACK, 1, 10, 2, false, List.of(first, second),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        String result = directTools.recommendMusic("找一些Re0的歌");

        assertThat(result).contains("single keyword \"Re0\"", "STYX HELIX", "Stay Alive");
        assertThat(actionContext.actions().get(0).recommendation().tracks())
                .containsExactly(first, second);
        assertThat(actionContext.actions().get(0).recommendation().searchQuery()).isEqualTo("Re0");
        verifyNoInteractions(recommendationService);
    }

    @Test
    void playlistSearchCreatesDedicatedPlaylistCardAction() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicKeywordExtractor keywordExtractor = mock(MusicKeywordExtractor.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService, keywordExtractor);
        var playlist = new QqMusicSearchBo.Playlist(
                "7868403012", "无畏契约高燃时刻", "游戏相关公开歌单", "https://img/playlist.jpg",
                "瓦友电台", 128_000, 36, "https://y.qq.com/n/ryqq/playlist/7868403012");
        String request = "找一个跟无畏契约相关的歌单给我";
        when(keywordExtractor.extract(request)).thenReturn(new MusicKeywordExtractor.ExtractedKeyword(
                "无畏契约", com.example.agent.model.bo.MusicSearchIntent.ENTITY_RELATED,
                com.example.agent.model.bo.MusicUnderstandingBo.unresolved()));
        when(qqMusicService.search(42L, memoryId.conversationId(), "无畏契约",
                QqMusicSearchType.PLAYLIST, 1, 12)).thenReturn(new QqMusicSearchBo(
                null, "无畏契约", QqMusicSearchType.PLAYLIST, 1, 12, 1, false,
                List.of(), List.of(), List.of(), List.of(playlist), List.of(), List.of(), List.of()));

        String result = directTools.searchQqPlaylists(request);

        assertThat(result).contains("无畏契约", "1 个公开歌单卡片");
        assertThat(actionContext.actions()).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(AgentActionType.SHOW_QQ_PLAYLIST_RESULTS);
            assertThat(action.recommendation()).isNull();
            assertThat(action.playlistSearch().keyword()).isEqualTo("无畏契约");
            assertThat(action.playlistSearch().playlists()).containsExactly(playlist);
        });
        verify(qqMusicService).search(42L, memoryId.conversationId(), "无畏契约",
                QqMusicSearchType.PLAYLIST, 1, 12);
        verifyNoInteractions(recommendationService);
    }

    @Test
    void randomPlaylistSkipsUnavailableTrackAndStartsTheNextVerifiedTrack() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService);
        MusicTrackBo unavailable = track("qq:unavailable1", "Unavailable");
        MusicTrackBo playable = track("qq:playable2", "Playable");
        QqPublicPlaylistBo candidate = publicPlaylist("7001", List.of());
        QqPublicPlaylistBo loaded = publicPlaylist("7001", List.of(unavailable, playable));
        when(qqMusicService.publicPlaylists(1, 16)).thenReturn(List.of(candidate));
        when(qqMusicService.publicPlaylist(42L, memoryId.conversationId(), "7001", 60, true))
                .thenReturn(loaded);
        when(qqMusicService.resolvePlayback("unavailable1", null))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "不可播放"));
        when(qqMusicService.resolvePlayback("playable2", null))
                .thenReturn(new QqMusicPlaybackBo(URI.create("https://audio.example/playable2"), "128"));

        String result = directTools.playRandomQqPublicPlaylist();

        assertThat(result).contains("Playable", "开始播放");
        assertThat(actionContext.actions()).extracting(action -> action.type()).containsExactly(
                AgentActionType.SHOW_MUSIC_RESULTS,
                AgentActionType.QUEUE_MUSIC_RESULTS,
                AgentActionType.PLAY_TRACK);
        assertThat(actionContext.actions().get(0).recommendation().tracks())
                .containsExactly(playable, unavailable);
        assertThat(actionContext.actions().get(2).track()).isEqualTo(playable);
    }

    @Test
    void randomPlaylistKeepsQueueWhenBoundedPlaybackProbeFindsNoPlayableTrack() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService);
        MusicTrackBo first = track("qq:blocked1", "Blocked One");
        MusicTrackBo second = track("qq:blocked2", "Blocked Two");
        QqPublicPlaylistBo candidate = publicPlaylist("7002", List.of());
        QqPublicPlaylistBo loaded = publicPlaylist("7002", List.of(first, second));
        when(qqMusicService.publicPlaylists(1, 16)).thenReturn(List.of(candidate));
        when(qqMusicService.publicPlaylist(42L, memoryId.conversationId(), "7002", 60, true))
                .thenReturn(loaded);
        when(qqMusicService.resolvePlayback(any(String.class), any()))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "不可播放"));

        String result = directTools.playRandomQqPublicPlaylist();

        assertThat(result).contains("未找到可播放曲目", "队列已保留");
        assertThat(actionContext.actions()).extracting(action -> action.type()).containsExactly(
                AgentActionType.SHOW_MUSIC_RESULTS,
                AgentActionType.QUEUE_MUSIC_RESULTS);
        assertThat(sessionStore.get(memoryId)).isPresent();
    }

    @Test
    void artistSearchCreatesLargeDossierActionWithGroundedSummariesAndCatalogPreviews() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicKeywordExtractor keywordExtractor = mock(MusicKeywordExtractor.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService, keywordExtractor);
        String request = "找歌手 Mili 并介绍她们";
        var artist = new QqMusicSearchBo.Artist(
                "0030xQJo2D8d6H", "0030xQJo2D8d6H", "Mili", "https://img/artist.jpg",
                120, 12, 8, "https://y.qq.com/n/ryqq/singer/0030xQJo2D8d6H");
        var similarArtist = new QqMusicSearchBo.Artist(
                "009similar", "009similar", "Miliyah", "https://img/similar.jpg",
                90, 10, 4, "https://y.qq.com/n/ryqq/singer/009similar");
        when(keywordExtractor.extract(request)).thenReturn(new MusicKeywordExtractor.ExtractedKeyword(
                "歌手 Mili 并介绍她们", com.example.agent.model.bo.MusicSearchIntent.ARTIST,
                com.example.agent.model.bo.MusicUnderstandingBo.unresolved()));
        when(qqMusicService.search(42L, memoryId.conversationId(), "Mili",
                QqMusicSearchType.ARTIST, 1, 5)).thenReturn(new QqMusicSearchBo(
                null, "Mili", QqMusicSearchType.ARTIST, 1, 5, 2, false,
                List.of(), List.of(artist, similarArtist), List.of(), List.of(), List.of(), List.of(), List.of()));
        var album = new QqArtistDetailBo.Album(
                "001album", "Millennium Mother", "https://img/album.jpg", "2018-04-25",
                "录音室专辑", 12, "https://y.qq.com/n/ryqq/albumDetail/001album");
        when(qqMusicService.artist(42L, memoryId.conversationId(), artist.mid(), 1, 12, 1, 8))
                .thenReturn(new QqArtistDetailBo(
                        null, artist.mid(), "Mili", artist.imageUrl(), "", "2012", "日本",
                        "Mili 于 2012 年成立，以融合古典、电子与另类流行的创作风格著称。代表作曾进入多个音乐榜单。",
                        artist.externalUrl(), 120, 12, 1, 12, true, 1, 8, true,
                        List.of(track("qq:mili:1", "Iron Lotus")), List.of(album), "qq-artist-v1",
                        com.example.agent.model.bo.MusicPersonalizationStatus.DISABLED));

        String result = directTools.searchQqArtists(request);

        assertThat(result).contains("最佳匹配艺人的大型档案卡", "Mili", "完整目录");
        assertThat(actionContext.actions()).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(AgentActionType.SHOW_QQ_ARTIST_RESULTS);
            assertThat(action.artistSearch().keyword()).isEqualTo("Mili");
            assertThat(action.artistSearch().artists()).singleElement().satisfies(profile -> {
                assertThat(profile.name()).isEqualTo("Mili");
                assertThat(profile.tracks()).extracting(MusicTrackBo::name).containsExactly("Iron Lotus");
                assertThat(profile.albums()).extracting(QqArtistDetailBo.Album::name)
                        .containsExactly("Millennium Mother");
                assertThat(profile.achievementSummary()).contains("代表作", "120 首歌曲", "12 张专辑");
                assertThat(profile.styleSummary()).contains("古典", "电子", "另类流行");
            });
        });
        verify(qqMusicService, never()).artist(42L, memoryId.conversationId(), similarArtist.mid(), 1, 12, 1, 8);
    }

    @Test
    void genericPlaylistRecommendationSearchesProfileDirectionsInsteadOfInstructionWords() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicKeywordExtractor keywordExtractor = mock(MusicKeywordExtractor.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService, keywordExtractor);
        when(keywordExtractor.extract("歌单推荐")).thenReturn(new MusicKeywordExtractor.ExtractedKeyword(
                "歌单推荐", com.example.agent.model.bo.MusicSearchIntent.DISCOVERY,
                com.example.agent.model.bo.MusicUnderstandingBo.unresolved()));
        when(personalizationService.profile(42L)).thenReturn(profile(
                List.of(insight("GENRE", "曲风", "独立摇滚", "L1", 1.0),
                        insight("ARTIST", "艺人", "Mili", "L2", 0.84)), List.of()));
        var rock = new QqMusicSearchBo.Playlist(
                "7001", "独立摇滚精选", "", "https://img/rock", "摇滚电台", 20_000, 30, "https://qq/7001");
        var mili = new QqMusicSearchBo.Playlist(
                "7002", "Mili Collection", "", "https://img/mili", "Mili Fans", 10_000, 20, "https://qq/7002");
        when(qqMusicService.search(42L, memoryId.conversationId(), "独立摇滚",
                QqMusicSearchType.PLAYLIST, 1, 8)).thenReturn(playlistResult("独立摇滚", rock));
        when(qqMusicService.search(42L, memoryId.conversationId(), "Mili",
                QqMusicSearchType.PLAYLIST, 1, 8)).thenReturn(playlistResult("Mili", mili));

        String result = directTools.searchQqPlaylists("歌单推荐");

        assertThat(result).contains("独立摇滚 · Mili", "结合音乐画像");
        assertThat(actionContext.actions()).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(AgentActionType.SHOW_QQ_PLAYLIST_RESULTS);
            assertThat(action.playlistSearch().keyword()).isEqualTo("独立摇滚 · Mili");
            assertThat(action.playlistSearch().explanation()).contains("曲风“独立摇滚”", "艺人“Mili”");
            assertThat(action.playlistSearch().playlists()).containsExactly(rock, mili);
        });
        verify(qqMusicService).search(42L, memoryId.conversationId(), "独立摇滚",
                QqMusicSearchType.PLAYLIST, 1, 8);
        verify(qqMusicService).search(42L, memoryId.conversationId(), "Mili",
                QqMusicSearchType.PLAYLIST, 1, 8);
    }

    @Test
    void genericTrackRecommendationUsesPersonalizedRecommendationPipeline() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicKeywordExtractor keywordExtractor = mock(MusicKeywordExtractor.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService, keywordExtractor);
        when(keywordExtractor.extract("给我推荐一些歌")).thenReturn(new MusicKeywordExtractor.ExtractedKeyword(
                "歌曲推荐", com.example.agent.model.bo.MusicSearchIntent.DISCOVERY,
                com.example.agent.model.bo.MusicUnderstandingBo.unresolved()));
        when(personalizationService.profile(42L)).thenReturn(profile(
                List.of(insight("GENRE", "曲风", "独立摇滚", "L1", 1.0)), List.of()));
        var recommendation = new MusicRecommendationBo(
                "独立摇滚", "独立摇滚", "画像排序结果", List.of("qq"),
                List.of(track("qq:profile:1", "Profile Match")));
        when(recommendationService.recommend(new MusicRecommendationAo(
                42L, memoryId.conversationId(), "独立摇滚", 1, 10))).thenReturn(recommendation);

        String result = directTools.recommendMusic("给我推荐一些歌");

        assertThat(result).contains("Profile Match");
        assertThat(actionContext.actions().get(0).recommendation().explanation())
                .contains("结合音乐画像", "曲风“独立摇滚”", "画像排序结果");
        verify(recommendationService).recommend(new MusicRecommendationAo(
                42L, memoryId.conversationId(), "独立摇滚", 1, 10));
        verifyNoInteractions(qqMusicService);
    }

    @Test
    void coordinatorProfileUsesPreparedContractWithoutRereadingProfile() {
        QqMusicService qqMusicService = mock(QqMusicService.class);
        MusicKeywordExtractor keywordExtractor = mock(MusicKeywordExtractor.class);
        MusicAgentTools directTools = new MusicAgentTools(recommendationService, personalizationService,
                sessionStore, actionContext, qqMusicService, keywordExtractor);
        String request = "根据我的喜好推荐适合跑步的歌";
        var proposed = new com.example.agent.model.bo.MusicSearchPlan(
                com.example.agent.model.bo.MusicSearchIntent.DISCOVERY, null, List.of(), null,
                List.of(), List.of(), List.of("跑步"), List.of(), 0.9, null);
        when(keywordExtractor.extract(request)).thenReturn(new MusicKeywordExtractor.ExtractedKeyword(
                "跑步", com.example.agent.model.bo.MusicSearchIntent.DISCOVERY,
                com.example.agent.model.bo.MusicUnderstandingBo.unresolved(), proposed));
        UserTasteContext profile = new UserTasteContext("STABLE", "画像稳定", true,
                100, 40, 3_600_000, 0.8,
                List.of(new UserTasteContext.Signal("GENRE", "独立摇滚", "明确喜欢", 1, "like:rock")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var recommendation = new MusicRecommendationBo(
                request, "跑步 独立摇滚", "画像排序结果", List.of("qq"),
                List.of(track("qq:profile:2", "Running Match")));
        when(recommendationService.recommendPrepared(any(PreparedMusicRecommendationAo.class)))
                .thenReturn(recommendation);

        String result = directTools.recommendMusic(request, profile, true);

        ArgumentCaptor<PreparedMusicRecommendationAo> prepared =
                ArgumentCaptor.forClass(PreparedMusicRecommendationAo.class);
        verify(recommendationService).recommendPrepared(prepared.capture());
        assertThat(prepared.getValue().command().description()).isEqualTo(request);
        assertThat(prepared.getValue().command().refreshBatch()).isTrue();
        assertThat(prepared.getValue().searchSeed()).isEqualTo("跑步 独立摇滚");
        assertThat(prepared.getValue().preferredTerms()).containsExactly("独立摇滚");
        assertThat(result).contains("Running Match");
        verifyNoInteractions(personalizationService);
        verifyNoInteractions(qqMusicService);
    }

    @Test
    void queueActionUsesLatestResultsInTheSameConversation() {
        MusicRecommendationBo recommendation = new MusicRecommendationBo(
                "Mili", "Mili", "找到歌曲", List.of("qq"),
                List.of(track("qq:1", "Ga1ahad and Scientific Witchery"), track("qq:2", "String Theocracy")));
        when(recommendationService.recommend(new MusicRecommendationAo(
                42L, memoryId.conversationId(), "Mili", 1, 10))).thenReturn(recommendation);

        tools.recommendMusic("Mili");
        String result = tools.queueLatestRecommendations();

        assertThat(result).contains("2 tracks");
        assertThat(actionContext.actions()).extracting(action -> action.type())
                .containsExactly(AgentActionType.SHOW_MUSIC_RESULTS, AgentActionType.QUEUE_MUSIC_RESULTS);
    }

    @Test
    void playbackWithoutSearchDoesNotCreateAnUnsafeUiAction() {
        String result = tools.playRecommendedTrack(1);

        assertThat(result).contains("no recent music results");
        assertThat(actionContext.actions()).isEmpty();
    }

    @Test
    void loadsAnotherPageFromTheLatestSearchContext() {
        MusicRecommendationBo firstPage = new MusicRecommendationBo(
                "Mili", "Mili", "第1页", List.of("qq"), List.of(track("qq:1", "Iron Lotus")),
                1, 10, true, 20);
        MusicRecommendationBo secondPage = new MusicRecommendationBo(
                "Mili", "Mili", "第2页", List.of("qq"), List.of(track("qq:11", "RTRT")),
                2, 10, false, 20);
        when(recommendationService.recommend(new MusicRecommendationAo(
                42L, memoryId.conversationId(), "Mili", 1, 10))).thenReturn(firstPage);
        when(recommendationService.recommend(new MusicRecommendationAo(
                42L, memoryId.conversationId(), "Mili", 2, 10))).thenReturn(secondPage);

        tools.recommendMusic("Mili");
        String result = tools.loadMusicResultsPage(2);

        assertThat(result).contains("page 2", "RTRT");
        assertThat(actionContext.actions()).extracting(action -> action.type())
                .containsExactly(AgentActionType.SHOW_MUSIC_RESULTS, AgentActionType.SHOW_MUSIC_RESULTS);
        assertThat(actionContext.actions().get(1).recommendation().page()).isEqualTo(2);
    }

    @Test
    void summarizesTheStoredProfileWithoutCreatingRecommendationActions() {
        var insight = new MusicProfileInsightVo("GENRE", "曲风", "独立摇滚", 1,
                "L2", 0.84, 5, "由 5 条有效行为推断");
        var summary = new MusicProfileSummaryVo("FORMING", "初步形成", "你的音乐偏好轮廓已初步形成",
                "画像基于 1 条明确偏好、1 条有效推断，以及 8 次推荐中的 6 条有效反馈。",
                "中等", List.of(insight), List.of(), List.of("仅曝光但没有操作不会被当作负反馈。"),
                java.time.LocalDateTime.now());
        when(personalizationService.profile(42L))
                .thenReturn(new MusicProfileVo(List.of(), List.of(), 6, 8, summary));

        String result = tools.summarizeMusicProfile();

        assertThat(result).contains("画像阶段：初步形成", "独立摇滚", "置信度 84%", "不会被当作负反馈");
        assertThat(actionContext.actions()).isEmpty();
    }

    private static MusicTrackBo track(String id, String name) {
        return new MusicTrackBo(id, name, List.of("Mili"), "Album", "https://img", 180_000,
                "https://source", "qq", "audio", "/api/music/qq/playback/1", null);
    }

    private static QqMusicSearchBo playlistResult(String keyword, QqMusicSearchBo.Playlist playlist) {
        return new QqMusicSearchBo(null, keyword, QqMusicSearchType.PLAYLIST, 1, 8, 1, false,
                List.of(), List.of(), List.of(), List.of(playlist), List.of(), List.of(), List.of());
    }

    private static QqPublicPlaylistBo publicPlaylist(String id, List<MusicTrackBo> tracks) {
        return new QqPublicPlaylistBo(id, "测试公开歌单", "", "https://img/playlist", "测试用户",
                "https://img/avatar", 1_000, tracks.size(), List.of("测试"),
                "https://y.qq.com/n/ryqq/playlist/" + id, UUID.randomUUID(), tracks,
                "qq-public-playlist-v1", com.example.agent.model.bo.MusicPersonalizationStatus.DISABLED);
    }

    private static MusicProfileVo profile(List<MusicProfileInsightVo> likes,
                                          List<MusicProfileInsightVo> avoids) {
        var summary = new MusicProfileSummaryVo("FORMING", "初步形成", "画像摘要", "画像概览",
                "中等", likes, avoids, List.of(), java.time.LocalDateTime.now());
        return new MusicProfileVo(List.of(), List.of(), 6, 8, summary);
    }

    private static MusicProfileInsightVo insight(String type, String label, String value,
                                                 String layer, double confidence) {
        return new MusicProfileInsightVo(type, label, value, 1, layer, confidence, 3, "测试证据");
    }
}
