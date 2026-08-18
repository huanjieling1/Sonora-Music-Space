package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.agent.intent.MusicIntentAgent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MusicSupportSuggestionPlannerTest {
    @Test
    void selectsOnlyLoadedProactiveSkillsAndKeepsAlternativesCapabilityBacked() {
        MusicSupportSuggestionPlanner planner = new MusicSupportSuggestionPlanner();
        MusicSupportContext context = new MusicSupportContext(
                MusicSupportContext.InteractionType.SUPPORT_SEEKING,
                MusicSupportContext.EmotionalSignal.SADNESS,
                MusicSupportContext.SupportGoal.SOOTHE, 0.9,
                "温柔、舒缓、不过分悲伤");

        var plan = planner.plan(context).orElseThrow();

        assertThat(plan.skillId()).isEqualTo("music-discovery");
        assertThat(plan.executionRoute()).isEqualTo(MusicAgentRoute.MUSIC_DISCOVERY);
        assertThat(plan.expectedEvidence()).isEqualTo(AgentActionType.SHOW_MUSIC_RESULTS);
        assertThat(plan.executionRequest()).contains("温柔、舒缓、不过分悲伤")
                .doesNotContain("播放", "长期画像");
        assertThat(MusicIntentAgent.wantsPlayback(plan.executionRequest())).isFalse();
        assertThat(plan.followUps()).extracting(value -> value.capabilityId())
                .contains("music-discovery", "qq-public-playlists");
    }
}
