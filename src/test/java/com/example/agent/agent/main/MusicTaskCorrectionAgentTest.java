package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicTaskEvaluation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicTaskCorrectionAgentTest {
    private final MusicTaskCorrectionAgent agent = new MusicTaskCorrectionAgent();

    @Test
    void givesChildAgentExplicitCorrectionWithoutChangingOriginalGoal() {
        var original = new MusicAgentTurn(1, UUID.randomUUID(), "五月天的歌单");
        var evaluation = MusicTaskEvaluation.revise(
                "用户要求歌单，但结果没有歌单卡片", "只返回真实 QQ 音乐歌单卡片");

        var corrected = agent.correct(original, evaluation, 2);

        assertThat(corrected.request()).isEqualTo(original.request());
        assertThat(corrected.executionDirective()).contains("未通过主 Agent 验收", "歌单卡片");
        assertThat(corrected.refreshBatch()).isTrue();
    }

    @Test
    void transientRetryKeepsTheSameTurnAndDoesNotPolluteSearchWording() {
        var original = new MusicAgentTurn(1, UUID.randomUUID(), "五月天的歌单");
        var evaluation = MusicTaskEvaluation.revise("曲库网络暂时不可用", "重新调用真实数据源");

        assertThat(agent.correct(original, evaluation, 2)).isSameAs(original);
    }
}
