package com.example.agent.tools;

import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
