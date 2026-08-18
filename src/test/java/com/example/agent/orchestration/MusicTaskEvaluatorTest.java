package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.model.bo.AgentActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicTaskEvaluatorTest {
    @Test
    void rejectsTrackCardsWhenTheValidatedTargetIsPlaylist() {
        MusicTaskEvaluator evaluator = new MusicTaskEvaluator();

        var evaluation = evaluator.evaluate(
                new MusicExecutionResult(MusicAgentRoute.PLAYLIST_SEARCH, true, "已找到真实结果",
                        Set.of(AgentActionType.SHOW_MUSIC_RESULTS)),
                playlistUnderstanding());

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.retryable()).isTrue();
        assertThat(evaluation.decision()).isEqualTo(
                com.example.agent.agent.contract.MusicTaskEvaluation.Decision.REVISE);
        assertThat(evaluation.reason()).contains("用户要求歌单", "没有歌单卡片");
        assertThat(evaluation.correction()).contains("真实歌单卡片");
    }

    @Test
    void acceptsPlaylistCardsForAPlaylistRequest() {
        MusicTaskEvaluator evaluator = new MusicTaskEvaluator();

        var evaluation = evaluator.evaluate(
                new MusicExecutionResult(MusicAgentRoute.PLAYLIST_SEARCH, true, "已找到真实歌单",
                        Set.of(AgentActionType.SHOW_QQ_PLAYLIST_RESULTS)),
                playlistUnderstanding());

        assertThat(evaluation.passed()).isTrue();
    }

    @Test
    void acceptsExpandedQueueAndPlaybackForRandomPlaylist() {
        MusicTaskEvaluator evaluator = new MusicTaskEvaluator();

        var evaluation = evaluator.evaluate(
                new MusicExecutionResult(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST, true,
                        "已加载歌单并开始播放",
                        Set.of(AgentActionType.SHOW_MUSIC_RESULTS,
                                AgentActionType.QUEUE_MUSIC_RESULTS, AgentActionType.PLAY_TRACK)),
                playlistUnderstanding(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST));

        assertThat(evaluation.passed()).isTrue();
    }

    @Test
    void acceptsAQueuedRandomPlaylistAsPartialSuccessWhenNoTrackIsPlayable() {
        MusicTaskEvaluator evaluator = new MusicTaskEvaluator();
        MusicExecutionResult result = MusicExecutionResult.partial(
                        MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST,
                        "歌单已经加载，当前账号未找到可播放曲目，队列已保留")
                .withEvidence(Set.of(AgentActionType.SHOW_MUSIC_RESULTS,
                        AgentActionType.QUEUE_MUSIC_RESULTS));

        var evaluation = evaluator.evaluate(result,
                playlistUnderstanding(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST));

        assertThat(result.partial()).isTrue();
        assertThat(evaluation.passed()).isTrue();
    }

    @Test
    void rejectsRandomPlaylistThatClaimsCompleteSuccessWithoutPlaybackEvidence() {
        MusicTaskEvaluator evaluator = new MusicTaskEvaluator();

        var evaluation = evaluator.evaluate(
                new MusicExecutionResult(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST, true,
                        "已完整执行随机歌单",
                        Set.of(AgentActionType.SHOW_MUSIC_RESULTS,
                                AgentActionType.QUEUE_MUSIC_RESULTS)),
                playlistUnderstanding(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST));

        assertThat(evaluation.passed()).isFalse();
        assertThat(evaluation.retryable()).isFalse();
        assertThat(evaluation.reason()).contains("没有经过验证的播放动作");
    }

    private static MusicIntentUnderstanding playlistUnderstanding() {
        return playlistUnderstanding(MusicAgentRoute.PLAYLIST_SEARCH);
    }

    private static MusicIntentUnderstanding playlistUnderstanding(MusicAgentRoute route) {
        var intent = new MusicIntentDraft(MusicIntentDraft.Action.RECOMMEND,
                MusicIntentDraft.Target.PLAYLIST, MusicIntentDraft.Mode.DISCOVERY,
                MusicIntentDraft.RankingMetric.NONE, MusicIntentDraft.TimeWindow.UNSPECIFIED,
                List.of("深夜"), true, List.of(), 0.95);
        return MusicIntentUnderstanding.routed(route, intent);
    }

}
