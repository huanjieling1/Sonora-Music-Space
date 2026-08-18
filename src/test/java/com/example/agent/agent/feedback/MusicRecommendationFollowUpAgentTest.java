package com.example.agent.agent.feedback;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.MusicFeedbackService;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MusicRecommendationFollowUpAgentTest {
    @Test
    void recordsBatchFeedbackAndExplicitPreferenceBeforeRerun() {
        MusicAgentSessionStore store = new MusicAgentSessionStore();
        MusicFeedbackService feedback = mock(MusicFeedbackService.class);
        MusicPersonalizationService personalization = mock(MusicPersonalizationService.class);
        MusicAgentTurn turn = new MusicAgentTurn(7, UUID.randomUUID(), "我不喜欢这些，我更喜欢 Mili 的歌");
        store.put(turn.memoryId(), recommendation());
        var plan = new MusicTurnPlan(true, true, true,
                List.of(new MusicPreferenceChange(MusicPreferenceType.ARTIST, "Mili", 1, true)),
                true, "推荐 Mili 的歌", true, 1, "");

        var outcome = new MusicRecommendationFollowUpAgent(store, feedback, personalization).apply(turn, plan);

        assertThat(outcome.recommendAgain()).isTrue();
        assertThat(outcome.refreshBatch()).isTrue();
        assertThat(outcome.recommendationRequest()).isEqualTo("推荐 Mili 的歌");
        assertThat(outcome.rejectedTrackCount()).isEqualTo(2);
        assertThat(outcome.acknowledgment()).contains("更喜欢 Mili").contains("会话中降权避开");
        verify(feedback, times(2)).record(eq(7L), any());
        verify(personalization).addPreference(eq(7L), any());
    }

    @Test
    void plainRefreshDoesNotWriteNegativeFeedbackOrPermanentPreference() {
        MusicFeedbackService feedback = mock(MusicFeedbackService.class);
        MusicPersonalizationService personalization = mock(MusicPersonalizationService.class);
        MusicAgentTurn turn = new MusicAgentTurn(7, UUID.randomUUID(), "换一批");
        var plan = new MusicTurnPlan(true, false, false, List.of(),
                true, "根据我刚才的反馈重新推荐一些歌", true, 1, "");

        var outcome = new MusicRecommendationFollowUpAgent(
                new MusicAgentSessionStore(), feedback, personalization).apply(turn, plan);

        assertThat(outcome.refreshBatch()).isTrue();
        assertThat(outcome.acknowledgment()).contains("还没展示过");
        assertThat(outcome.rejectedTrackCount()).isZero();
        verifyNoInteractions(feedback, personalization);
    }

    private static MusicRecommendationBo recommendation() {
        var first = new MusicTrackBo("t1", "歌曲一", List.of("歌手一"), "", "", 1000,
                "", "qq", "stream", "https://example.test/1.mp3", "");
        var second = new MusicTrackBo("t2", "歌曲二", List.of("歌手二"), "", "", 1000,
                "", "qq", "stream", "https://example.test/2.mp3", "");
        return new MusicRecommendationBo("旧推荐", "流行", "说明", List.of("qq"), List.of(first, second));
    }
}
