package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.impl.MusicAgentSessionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicContextualIntentAgentTest {
    @Test
    void compoundFollowUpRejectsBatchStoresArtistAndRequestsOneRerun() {
        MusicAgentSessionStore store = new MusicAgentSessionStore();
        MusicAgentTurn turn = turn("我不喜欢这些我更喜欢mili的歌");
        store.put(turn.memoryId(), recommendation());
        MusicContextualIntentAgent agent = new MusicContextualIntentAgent(store,
                packet -> { throw new AssertionError("deterministic compound intent should not call LLM"); });

        MusicTurnPlan plan = agent.analyze(turn).orElseThrow();

        assertThat(plan.latestRecommendationReferenced()).isTrue();
        assertThat(plan.rejectLatestBatch()).isTrue();
        assertThat(plan.recommendAgain()).isTrue();
        assertThat(plan.recommendationRequest()).isEqualTo("推荐 mili 的歌");
        assertThat(plan.preferences()).containsExactly(
                new MusicPreferenceChange(MusicPreferenceType.ARTIST, "mili", 1, true));
    }

    @Test
    void missingLatestResultTurnsPronounIntoClarification() {
        MusicAgentTurn turn = turn("这些我不喜欢，换一批");
        MusicContextualIntentAgent agent = new MusicContextualIntentAgent(new MusicAgentSessionStore(),
                packet -> MusicTurnPlan.none());

        MusicTurnPlan plan = agent.analyze(turn).orElseThrow();

        assertThat(plan.recommendAgain()).isFalse();
        assertThat(plan.rejectLatestBatch()).isFalse();
        assertThat(plan.clarificationQuestion()).contains("哪一批");
    }

    @Test
    void refreshBatchRequestsNewResultsWithoutCreatingNegativeTaste() {
        MusicAgentSessionStore store = new MusicAgentSessionStore();
        MusicAgentTurn turn = turn("换一批");
        store.put(turn.memoryId(), recommendation());
        MusicContextualIntentAgent agent = new MusicContextualIntentAgent(store,
                packet -> { throw new AssertionError("refresh intent should be deterministic"); });

        MusicTurnPlan plan = agent.analyze(turn).orElseThrow();

        assertThat(plan.refreshBatch()).isTrue();
        assertThat(plan.recommendAgain()).isTrue();
        assertThat(plan.rejectLatestBatch()).isFalse();
        assertThat(plan.preferences()).isEmpty();
    }

    @Test
    void rejectsModelPreferenceThatWasNotLiteralInCurrentRequest() {
        MusicAgentSessionStore store = new MusicAgentSessionStore();
        MusicAgentTurn turn = turn("刚才的结果有一点偏离我的意思");
        store.put(turn.memoryId(), recommendation());
        MusicFollowUpPlanner planner = packet -> new MusicTurnPlan(true, true, true,
                List.of(new MusicPreferenceChange(MusicPreferenceType.ARTIST, "周杰伦", 1, true)),
                true, "重新推荐一些歌", 0.9, "");

        MusicTurnPlan plan = new MusicContextualIntentAgent(store, planner).analyze(turn).orElseThrow();

        assertThat(plan.preferences()).isEmpty();
        assertThat(plan.rejectLatestBatch()).isTrue();
    }

    @Test
    void unrelatedConversationNeverInvokesContextPlanner() {
        MusicContextualIntentAgent agent = new MusicContextualIntentAgent(new MusicAgentSessionStore(),
                packet -> { throw new AssertionError("ordinary chat must not invoke contextual planner"); });

        assertThat(agent.analyze(turn("你好，Sonora"))).isEmpty();
    }

    private static MusicAgentTurn turn(String request) {
        return new MusicAgentTurn(1, UUID.randomUUID(), request);
    }

    private static MusicRecommendationBo recommendation() {
        var track = new MusicTrackBo("t1", "旧推荐", List.of("旧歌手"), "专辑", "", 1000,
                "", "qq", "stream", "https://example.test/a.mp3", "");
        return new MusicRecommendationBo("旧请求", "旧关键词", "说明", List.of("qq"), List.of(track));
    }
}
