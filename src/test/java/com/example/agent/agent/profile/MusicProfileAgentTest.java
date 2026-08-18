package com.example.agent.agent.profile;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.UserTasteContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MusicProfileAgentTest {
    @Test
    void givesFlexibleNarratorOnlyAnEvidenceCarryingSnapshot() {
        UserTasteContext context = context();
        MusicProfileContextReader reader = userId -> context;
        MusicProfileNarrator narrator = mock(MusicProfileNarrator.class);
        when(narrator.narrate(context, "分析我的音乐画像"))
                .thenReturn("你对 Mili 的偏好持续且有明确播放证据。");
        var agent = new MusicProfileAgent(reader, narrator);

        var result = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "分析我的音乐画像"));

        assertThat(result.languageModelApplied()).isTrue();
        assertThat(result.answer()).contains("Mili", "播放证据");
        assertThat(result.context().labels()).allSatisfy(signal -> {
            assertThat(signal.evidenceId()).isNotBlank();
            assertThat(signal.confidence()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void narratorFailureFallsBackToAuditableFacts() {
        UserTasteContext context = context();
        MusicProfileNarrator narrator = (value, request) -> { throw new IllegalStateException("offline"); };
        var agent = new MusicProfileAgent(userId -> context, narrator);

        var result = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "总结我的偏好"));

        assertThat(result.languageModelApplied()).isFalse();
        assertThat(result.answer()).contains("Mili", "39 次")
                .doesNotContain("###", "- ", "\n")
                .hasSizeLessThanOrEqualTo(180);
    }

    @Test
    void rejectsNarrativeThatInfersPersonalAttributesOrInventsStatistics() {
        UserTasteContext context = context();
        MusicProfileNarrator narrator = (value, request) ->
                "你喜欢 Mili，所以可以推断你的职业；最近听了 999 次。";
        var agent = new MusicProfileAgent(userId -> context, narrator);

        var result = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "分析我的音乐画像"));

        assertThat(result.languageModelApplied()).isFalse();
        assertThat(result.answer()).doesNotContain("职业", "999").contains("Mili", "39 次");
    }

    @Test
    void neverUsesLanguageModelNarrativeWithoutMusicEvidence() {
        UserTasteContext empty = new UserTasteContext("EMPTY", "暂无画像", false,
                0, 0, 0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var agent = new MusicProfileAgent(userId -> empty, (value, request) -> "你已经形成稳定画像。" );

        var result = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "分析我的音乐画像"));

        assertThat(result.languageModelApplied()).isFalse();
        assertThat(result.answer()).contains("等待第一束光", "慢慢显影");
    }

    @Test
    void rejectsLongOrMarkdownNarrativeAndReturnsCompactPlainText() {
        UserTasteContext context = context();
        MusicProfileNarrator narrator = (value, request) -> """
                ### 用户画像概述
                **音乐偏好**：你喜欢 Mili。
                - 这是一个很长的报告，而不是摘要。
                """;
        var agent = new MusicProfileAgent(userId -> context, narrator);

        var result = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "我的用户画像是什么"));

        assertThat(result.languageModelApplied()).isFalse();
        assertThat(result.answer()).contains("Mili", "39 次")
                .doesNotContain("###", "**", "\n")
                .hasSizeLessThanOrEqualTo(180);
    }

    private static UserTasteContext context() {
        return new UserTasteContext("STABLE", "画像稳定", true, 180, 92, 5_880_000, 0.15,
                List.of(), List.of(),
                List.of(new UserTasteContext.Signal("USER_LABEL", "Mili深度听众",
                        "累计播放39次，占有效播放22%", 0.87, "label:artist-loyalty")),
                List.of(),
                List.of(new UserTasteContext.RankedItem("Mili", "9 首歌曲", 39, "artist:mili")),
                List.of(), List.of("画像只用于音乐排序"));
    }
}
