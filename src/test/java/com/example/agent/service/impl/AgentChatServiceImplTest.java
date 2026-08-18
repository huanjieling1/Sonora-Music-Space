package com.example.agent.service.impl;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.response.AgentResponseGuard;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.agent.contract.MusicProactiveSuggestion;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.orchestration.MusicAgentCoordinator;
import com.example.agent.tools.AgentActionContext;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatServiceImplTest {
    @Test
    void delegatesOneTurnToCoordinatorAndReturnsCollectedActions() {
        AgentActionContext actions = new AgentActionContext();
        MusicAgentCoordinator coordinator = mock(MusicAgentCoordinator.class);
        UUID conversationId = UUID.randomUUID();
        when(coordinator.orchestrate(any())).thenAnswer(invocation -> {
            MusicAgentTurn turn = invocation.getArgument(0);
            actions.add(AgentActionBo.showMusic(mock(MusicRecommendationBo.class)));
            return MusicAgentWorkflowState.start(turn, MusicAgentRoute.MUSIC_DISCOVERY)
                    .completed("已完成", "response");
        });
        var service = service(actions, coordinator);

        var reply = service.chat(7L, conversationId, "推荐一些歌");

        assertThat(reply.answer()).isEqualTo("已完成");
        assertThat(reply.actions()).hasSize(1);
        verify(coordinator).orchestrate(new MusicAgentTurn(7L, conversationId, "推荐一些歌"));
    }

    @Test
    void alwaysClearsThreadLocalActionsAfterTurn() {
        AgentActionContext actions = new AgentActionContext();
        MusicAgentCoordinator coordinator = mock(MusicAgentCoordinator.class);
        when(coordinator.orchestrate(any())).thenThrow(new IllegalStateException("boom"));
        var service = service(actions, coordinator);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.chat(7L, UUID.randomUUID(), "测试"));

        org.assertj.core.api.Assertions.assertThatThrownBy(actions::actions)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishesEvidenceOnlyProfileStoryForProfileTurns() {
        AgentActionContext actions = new AgentActionContext();
        MusicAgentCoordinator coordinator = mock(MusicAgentCoordinator.class);
        UserTasteContext context = new UserTasteContext("STABLE", "画像稳定", true,
                180, 92, 5_880_000, .64, List.of(), List.of(), List.of(),
                List.of(new UserTasteContext.RankedItem("Through Patches of Violet", "Mili", 12, "track:1")),
                List.of(new UserTasteContext.RankedItem("Mili", "9 首歌曲", 39, "artist:mili")),
                List.of(new UserTasteContext.RankedItem("独立音乐", "风格", 24, "tag:indie")), List.of());
        when(coordinator.orchestrate(any())).thenAnswer(invocation -> {
            MusicAgentTurn turn = invocation.getArgument(0);
            return MusicAgentWorkflowState.start(turn, MusicAgentRoute.PROFILE_ANALYSIS)
                    .withTasteContext(context).completed("Mili 是最明亮的一处坐标。", "profile");
        });
        var service = service(actions, coordinator);

        var reply = service.chat(7L, UUID.randomUUID(), "分析我的音乐画像");

        assertThat(reply.actions()).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(AgentActionType.SHOW_MUSIC_PROFILE_STORY);
            assertThat(action.profileStory().narrative()).contains("Mili");
            assertThat(action.profileStory().topArtists()).extracting("name").containsExactly("Mili");
            assertThat(action.profileStory().playCount()).isEqualTo(180);
        });
    }

    @Test
    void publishesCapabilityBackedQuickActionsForSupportTurns() {
        AgentActionContext actions = new AgentActionContext();
        MusicAgentCoordinator coordinator = mock(MusicAgentCoordinator.class);
        when(coordinator.orchestrate(any())).thenAnswer(invocation -> {
            MusicAgentTurn turn = invocation.getArgument(0);
            var support = new MusicSupportContext(MusicSupportContext.InteractionType.SUPPORT_SEEKING,
                    MusicSupportContext.EmotionalSignal.SADNESS, MusicSupportContext.SupportGoal.SOOTHE,
                    .9, "温柔舒缓");
            var plan = new MusicSupportSuggestionPlan("music-discovery", "音乐发现",
                    MusicAgentRoute.MUSIC_DISCOVERY, "推荐温柔的歌", MusicAutonomyLevel.READ_ONLY,
                    AgentActionType.SHOW_MUSIC_RESULTS,
                    List.of(new MusicProactiveSuggestion("再安静一点", "推荐更安静的歌",
                            "music-discovery", false)));
            return MusicAgentWorkflowState.start(turn, MusicAgentRoute.SUPPORTIVE_MUSIC)
                    .withSupport(support, plan).completed("我听见了。", "support-response");
        });
        var service = service(actions, coordinator);

        var reply = service.chat(7L, UUID.randomUUID(), "我现在不开心");

        assertThat(reply.actions()).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(AgentActionType.SHOW_PROACTIVE_SUGGESTIONS);
            assertThat(action.proactiveSuggestions().items()).singleElement()
                    .satisfies(item -> assertThat(item.capabilityId()).isEqualTo("music-discovery"));
        });
    }

    private static AgentChatServiceImpl service(AgentActionContext actions, MusicAgentCoordinator coordinator) {
        return new AgentChatServiceImpl(actions, coordinator,
                new AgentResponseGuard(new AgentCapabilityRegistry()));
    }
}
