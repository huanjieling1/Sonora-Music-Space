package com.example.agent.service.impl;

import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import com.example.agent.service.MusicPersonalizationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MusicRecommendationContextResolverTest {
    private final MusicPersonalizationService personalization = mock(MusicPersonalizationService.class);
    private final MusicRecommendationContextResolver resolver =
            new MusicRecommendationContextResolver(personalization);

    @Test
    void replacesGenericRecommendationWordsWithReliableProfileDirections() {
        when(personalization.profile(7L)).thenReturn(profile(
                List.of(like("GENRE", "曲风", "独立摇滚", "L1", 1.0),
                        like("ARTIST", "艺人", "Mili", "L2", 0.86)),
                List.of(like("GENRE", "曲风", "重金属", "L1", 1.0))));

        var context = resolver.resolve(7L, "歌单推荐", MusicSearchIntent.DISCOVERY, "歌单推荐");

        assertThat(context.recommendation()).isTrue();
        assertThat(context.profileApplied()).isTrue();
        assertThat(context.searchDescription()).isEqualTo("独立摇滚 Mili");
        assertThat(context.playlistKeywords()).containsExactly("独立摇滚", "Mili");
        assertThat(context.rationale()).contains("曲风“独立摇滚”", "艺人“Mili”", "避开：曲风“重金属”");
    }

    @Test
    void keepsCurrentSceneAheadOfProfilePreferences() {
        when(personalization.profile(7L)).thenReturn(profile(
                List.of(like("GENRE", "曲风", "独立摇滚", "L1", 1.0)), List.of()));

        var context = resolver.resolve(7L, "推荐适合跑步的音乐", MusicSearchIntent.DISCOVERY, "跑步");

        assertThat(context.searchDescription()).startsWith("跑步 ");
        assertThat(context.playlistKeywords()).containsExactly("跑步", "独立摇滚");
        assertThat(context.rationale()).startsWith("优先满足你当前提出的“跑步”需求");
    }

    @Test
    void namedEntitySearchStaysLiteralAndDoesNotReadTheProfile() {
        var context = resolver.resolve(
                7L, "找无畏契约相关音乐", MusicSearchIntent.ENTITY_RELATED, "无畏契约");

        assertThat(context.recommendation()).isFalse();
        assertThat(context.searchDescription()).isEqualTo("找无畏契约相关音乐");
        assertThat(context.playlistKeywords()).containsExactly("无畏契约");
        verifyNoInteractions(personalization);
    }

    @Test
    void emptyProfileUsesAnExplicitColdStartDirection() {
        when(personalization.profile(7L)).thenReturn(profile(List.of(), List.of()));

        var context = resolver.resolve(7L, "给我推荐一些歌", MusicSearchIntent.DISCOVERY, "给我推荐一些歌");

        assertThat(context.profileApplied()).isFalse();
        assertThat(context.searchDescription()).isEqualTo("热门音乐");
        assertThat(context.playlistKeywords()).isEmpty();
        assertThat(context.rationale()).contains("没有可靠画像", "热门内容", "冷启动推荐");
    }

    @Test
    void suppliedWorkflowProfileIsUsedWithoutReadingTheRepositoryAgain() {
        UserTasteContext context = new UserTasteContext("STABLE", "画像稳定", true,
                100, 40, 3_600_000, 0.8,
                List.of(new UserTasteContext.Signal("GENRE", "独立摇滚", "明确喜欢", 1.0, "like:rock")),
                List.of(new UserTasteContext.Signal("GENRE", "重金属", "高跳过率", 0.8, "avoid:metal")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        var result = resolver.resolve(7L, "推荐适合跑步的音乐", MusicSearchIntent.DISCOVERY,
                "跑步", context);

        assertThat(result.searchDescription()).isEqualTo("跑步 独立摇滚");
        assertThat(result.preferredTerms()).containsExactly("独立摇滚");
        assertThat(result.avoidedTerms()).containsExactly("重金属");
        assertThat(result.rationale()).startsWith("优先满足你当前提出的“跑步”需求");
        verifyNoInteractions(personalization);
    }

    private static MusicProfileVo profile(List<MusicProfileInsightVo> likes,
                                          List<MusicProfileInsightVo> avoids) {
        var summary = new MusicProfileSummaryVo(
                likes.isEmpty() ? "EMPTY" : "FORMING", likes.isEmpty() ? "暂无画像" : "初步形成",
                "画像摘要", "画像概览", likes.isEmpty() ? "暂无" : "中等",
                likes, avoids, List.of(), LocalDateTime.now());
        return new MusicProfileVo(List.of(), List.of(), 0, 0, summary);
    }

    private static MusicProfileInsightVo like(String type, String label, String value,
                                              String layer, double confidence) {
        return new MusicProfileInsightVo(type, label, value, 1, layer, confidence, 3, "测试证据");
    }
}
