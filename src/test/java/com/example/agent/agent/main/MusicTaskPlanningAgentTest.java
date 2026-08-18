package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.intent.MusicIntentAgent;
import com.example.agent.orchestration.MusicPlanValidator;
import com.example.agent.orchestration.MusicWorkflowPlanner;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicTaskPlanningAgentTest {
    private final MusicIntentAgent intentAgent = new MusicIntentAgent();
    private final MusicTaskPlanningAgent planningAgent = new MusicTaskPlanningAgent(
            new MusicWorkflowPlanner(), new MusicPlanValidator());

    @Test
    void decomposesArtistPlaylistRequestIntoOrderedTasksWithAcceptanceCriteria() {
        var turn = new MusicAgentTurn(1, UUID.randomUUID(), "五月天的歌单");
        var understanding = intentAgent.analyze(turn);

        var plan = planningAgent.plan(turn, understanding, MusicAgentRoute.PLAYLIST_SEARCH, false);

        assertThat(plan.goal()).contains("五月天的歌单", "可验证结果");
        assertThat(plan.tasks()).extracting(value -> value.id())
                .containsExactly("intent", "execution", "verification", "response");
        assertThat(plan.tasks().stream().filter(value -> value.id().equals("execution")).findFirst().orElseThrow()
                .acceptanceCriteria()).anyMatch(value -> value.contains("真实 QQ 音乐歌单卡片"));
        assertThat(plan.tasks().stream().filter(value -> value.id().equals("verification")).findFirst().orElseThrow()
                .dependencies()).containsExactly("execution");
    }

    @Test
    void randomPlaylistPlanRequiresQueueAndPlayableTrackInsteadOfPlaylistCards() {
        var turn = new MusicAgentTurn(1, UUID.randomUUID(), "随机找歌单");
        var understanding = intentAgent.analyze(turn);

        var plan = planningAgent.plan(turn, understanding,
                MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST, false);

        var criteria = plan.tasks().stream().filter(value -> value.id().equals("execution"))
                .findFirst().orElseThrow().acceptanceCriteria();
        assertThat(criteria).anyMatch(value -> value.contains("建立播放队列"));
        assertThat(criteria).anyMatch(value -> value.contains("可播放歌曲") && value.contains("部分成功"));
        assertThat(criteria).noneMatch(value -> value.contains("歌单卡片"));
    }
}
