package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import com.example.agent.model.bo.AgentActionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicIntentArbiterTest {
    private final MusicIntentEvidenceExtractor extractor = new MusicIntentEvidenceExtractor();
    private final MusicIntentArbiter arbiter = new MusicIntentArbiter();

    @Test
    void emotionOnlyRequestRejectsUnsupportedTrendAndSelectsSupport() {
        MusicIntentDraft inventedTrend = new MusicIntentDraft(MusicIntentDraft.Action.RECOMMEND,
                MusicIntentDraft.Target.CHART, MusicIntentDraft.Mode.TRENDING,
                MusicIntentDraft.RankingMetric.HOTNESS, MusicIntentDraft.TimeWindow.RECENT,
                List.of(), false, List.of(), 0.95, MusicIntentDraft.Domain.MUSIC);
        MusicSupportContext support = celebration();

        var result = arbiter.arbitrate(
                MusicIntentUnderstanding.routed(MusicAgentRoute.QQ_TREND_DISCOVERY, inventedTrend),
                MusicAgentRoute.CONVERSATION, extractor.extract("我有点开心"), support, plan());

        assertThat(result.route()).isEqualTo(MusicAgentRoute.SUPPORTIVE_MUSIC);
        assertThat(result.reason()).contains("unsupported trend proposal rejected");
        assertThat(result.understanding().intent().rankingMetric())
                .isEqualTo(MusicIntentDraft.RankingMetric.NONE);
    }

    @Test
    void explicitTrendCommandWinsWhileEmotionRemainsContextOnly() {
        MusicIntentDraft trend = new MusicIntentDraft(MusicIntentDraft.Action.RECOMMEND,
                MusicIntentDraft.Target.TRACK, MusicIntentDraft.Mode.TRENDING,
                MusicIntentDraft.RankingMetric.HOTNESS, MusicIntentDraft.TimeWindow.RECENT,
                List.of(), false, List.of(), 0.95, MusicIntentDraft.Domain.MUSIC);

        var result = arbiter.arbitrate(
                MusicIntentUnderstanding.routed(MusicAgentRoute.QQ_TREND_DISCOVERY, trend),
                MusicAgentRoute.QQ_TREND_DISCOVERY,
                extractor.extract("我有点开心，推荐几首最近的热歌"), celebration(), plan());

        assertThat(result.route()).isEqualTo(MusicAgentRoute.QQ_TREND_DISCOVERY);
        assertThat(result.evidence().trend()).isTrue();
    }

    private static MusicSupportContext celebration() {
        return new MusicSupportContext(MusicSupportContext.InteractionType.SUPPORT_SEEKING,
                MusicSupportContext.EmotionalSignal.CELEBRATION,
                MusicSupportContext.SupportGoal.ENERGIZE, 0.9, "明亮、有律动感");
    }

    private static MusicSupportSuggestionPlan plan() {
        return new MusicSupportSuggestionPlan("music-discovery", "音乐发现",
                MusicAgentRoute.MUSIC_DISCOVERY, "推荐明亮有律动感的真实歌曲",
                MusicAutonomyLevel.READ_ONLY, AgentActionType.SHOW_MUSIC_RESULTS, List.of());
    }
}
