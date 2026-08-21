package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicIntentDraft;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicIntentAgentTest {
    private final MusicIntentAgent agent = new MusicIntentAgent();

    @Test
    void routesProfileOnlyRequestsWithoutStartingRecommendation() {
        assertThat(agent.classify("分析并总结我的音乐画像"))
                .isEqualTo(MusicAgentRoute.PROFILE_ANALYSIS);
    }

    @Test
    void routesFavoriteArtistProfileAsACompositeWorkflow() {
        assertThat(agent.classify("把你认为的我最喜欢的歌手的个人资料找出来"))
                .isEqualTo(MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE);
        assertThat(MusicIntentAgent.shouldUseRecommendationProfile(
                "把你认为的我最喜欢的歌手的个人资料找出来")).isTrue();
    }

    @Test
    void currentRecommendationRequestOverridesProfileWording() {
        assertThat(agent.classify("根据我的画像推荐一些适合夜晚的歌"))
                .isEqualTo(MusicAgentRoute.MUSIC_DISCOVERY);
    }

    @Test
    void routesStrictToolWorkflowsAndFollowUps() {
        assertThat(agent.classify("随机歌单给我")).isEqualTo(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST);
        assertThat(agent.classify("找一个无畏契约歌单")).isEqualTo(MusicAgentRoute.PLAYLIST_SEARCH);
        assertThat(agent.classify("介绍歌手 Mili")).isEqualTo(MusicAgentRoute.ARTIST_LOOKUP);
        assertThat(agent.classify("播放第二首")).isEqualTo(MusicAgentRoute.RESULT_PLAYBACK);
        assertThat(agent.classify("下一页")).isEqualTo(MusicAgentRoute.RESULT_NAVIGATION);
        assertThat(agent.classify("全部加入队列")).isEqualTo(MusicAgentRoute.QUEUE_CONTROL);
    }

    @Test
    void onlyOpenRecommendationsRequestProfileCollaboration() {
        assertThat(MusicIntentAgent.shouldUseRecommendationProfile("根据我的画像推荐适合夜晚的歌")).isTrue();
        assertThat(MusicIntentAgent.shouldUseRecommendationProfile("来点适合学习的音乐")).isTrue();
        assertThat(MusicIntentAgent.shouldUseRecommendationProfile("播放周杰伦的晴天")).isFalse();
    }

    @Test
    void treatsPlaylistAsTheRequestedObjectInsteadOfReturningTrackCards() {
        var understanding = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "来点适合深夜听的歌单"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.PLAYLIST_SEARCH);
        assertThat(understanding.intent().target()).isEqualTo(MusicIntentDraft.Target.PLAYLIST);
        assertThat(understanding.intent().scenes()).contains("深夜");
    }

    @Test
    void routesTrendingRequestToVerifiedQqChartCapability() {
        var understanding = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "推荐一下最近热度最高的音乐"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.QQ_TREND_DISCOVERY);
        assertThat(understanding.supported()).isTrue();
        assertThat(understanding.intent().mode()).isEqualTo(MusicIntentDraft.Mode.TRENDING);
        assertThat(understanding.intent().rankingMetric()).isEqualTo(MusicIntentDraft.RankingMetric.HOTNESS);
        assertThat(understanding.intent().timeWindow()).isEqualTo(MusicIntentDraft.TimeWindow.RECENT);
    }

    @Test
    void asksAPlaylistSpecificQuestionForAnIncompletePlaylistRequest() {
        var understanding = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "歌单"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.SCOPE_CLARIFICATION);
        assertThat(understanding.userMessage()).contains("按自己的口味推荐", "主题、歌手或场景");
        assertThat(understanding.userMessage()).doesNotContain("目前已加载");
    }

    @Test
    void infersSearchActionFromArtistPlaylistRelation() {
        var understanding = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "五月天的歌单"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.PLAYLIST_SEARCH);
        assertThat(understanding.intent().action()).isEqualTo(MusicIntentDraft.Action.SEARCH);
        assertThat(understanding.intent().target()).isEqualTo(MusicIntentDraft.Target.PLAYLIST);
    }

    @Test
    void correctionWithExplicitArtistRelationBecomesExecutablePlaylistSearch() {
        var understanding = agent.analyze(new MusicAgentTurn(1, UUID.randomUUID(), "我是说歌手五月天的歌单"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.PLAYLIST_SEARCH);
        assertThat(understanding.intent().action()).isEqualTo(MusicIntentDraft.Action.SEARCH);
    }

    @Test
    void correctionKeepsPreviousSceneAndChangesTheRequestedObject() {
        MusicIntentContextStore store = new MusicIntentContextStore();
        MusicIntentAgent contextual = new MusicIntentAgent(request -> Optional.empty(), store);
        UUID conversationId = UUID.randomUUID();
        contextual.analyze(new MusicAgentTurn(1, conversationId, "来点适合深夜听的音乐"));

        var corrected = contextual.analyze(new MusicAgentTurn(1, conversationId, "我是说歌单推荐"));

        assertThat(corrected.route()).isEqualTo(MusicAgentRoute.PLAYLIST_SEARCH);
        assertThat(corrected.intent().target()).isEqualTo(MusicIntentDraft.Target.PLAYLIST);
        assertThat(corrected.intent().scenes()).contains("深夜");
    }

    @Test
    void literalPlaylistTargetOverridesAnIncorrectModelProposal() {
        var wrongModel = new MusicIntentDraft(MusicIntentDraft.Action.SEARCH, MusicIntentDraft.Target.TRACK,
                MusicIntentDraft.Mode.EXACT, MusicIntentDraft.RankingMetric.NONE,
                MusicIntentDraft.TimeWindow.UNSPECIFIED, java.util.List.of(), false,
                java.util.List.of(), 0.95);
        MusicIntentAgent validated = new MusicIntentAgent(request -> Optional.of(wrongModel),
                new MusicIntentContextStore());

        var understanding = validated.analyze(
                new MusicAgentTurn(1, UUID.randomUUID(), "来点适合深夜听的歌单"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.PLAYLIST_SEARCH);
        assertThat(understanding.intent().target()).isEqualTo(MusicIntentDraft.Target.PLAYLIST);
    }

    @Test
    void deterministicDomainBoundaryOverridesAnIncorrectMusicProposal() {
        var wrongModel = new MusicIntentDraft(MusicIntentDraft.Action.SEARCH, MusicIntentDraft.Target.TRACK,
                MusicIntentDraft.Mode.EXACT, MusicIntentDraft.RankingMetric.NONE,
                MusicIntentDraft.TimeWindow.UNSPECIFIED, java.util.List.of(), false,
                java.util.List.of(), 0.95, MusicIntentDraft.Domain.MUSIC);
        MusicIntentAgent validated = new MusicIntentAgent(request -> Optional.of(wrongModel),
                new MusicIntentContextStore());

        var understanding = validated.analyze(
                new MusicAgentTurn(1, UUID.randomUUID(), "帮我查一下明天的天气"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.CONVERSATION);
        assertThat(understanding.intent().domain()).isEqualTo(MusicIntentDraft.Domain.OTHER);
    }

    @Test
    void rejectsModelInventedTrendWithoutLiteralTrendEvidence() {
        var wrongModel = new MusicIntentDraft(MusicIntentDraft.Action.RECOMMEND,
                MusicIntentDraft.Target.CHART, MusicIntentDraft.Mode.TRENDING,
                MusicIntentDraft.RankingMetric.HOTNESS, MusicIntentDraft.TimeWindow.RECENT,
                java.util.List.of(), false, java.util.List.of(), 0.96, MusicIntentDraft.Domain.MUSIC);
        MusicIntentAgent validated = new MusicIntentAgent(request -> Optional.of(wrongModel),
                new MusicIntentContextStore());

        var understanding = validated.analyze(
                new MusicAgentTurn(1, UUID.randomUUID(), "我有点开心"));

        assertThat(understanding.route()).isEqualTo(MusicAgentRoute.CONVERSATION);
        assertThat(understanding.intent().mode()).isNotEqualTo(MusicIntentDraft.Mode.TRENDING);
        assertThat(understanding.intent().rankingMetric()).isEqualTo(MusicIntentDraft.RankingMetric.NONE);
    }
}
