package com.example.agent.agent.execution;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.AgentToolAuthorizer;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.tools.AgentActionContext;
import com.example.agent.tools.MusicAgentTools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicExecutionAgentTest {
    @Test
    void dedicatedRouteCallsOnlyItsWhitelistedTool() {
        MusicAgentTools tools = mock(MusicAgentTools.class);
        when(tools.searchQqPlaylists("找一个通勤歌单")).thenReturn("已展示 3 个歌单");
        var agent = new MusicExecutionAgent(tools, new AgentActionContext(), new MusicAgentSessionStore(),
                new AgentToolAuthorizer(new AgentCapabilityRegistry()));
        MusicAgentTurn turn = turn("找一个通勤歌单");

        var result = agent.execute(turn, MusicAgentRoute.PLAYLIST_SEARCH);

        assertThat(result.successful()).isTrue();
        verify(tools).searchQqPlaylists(turn.request());
    }

    @Test
    void explicitPlaybackSearchExecutesSearchAndPlaybackOnce() {
        MusicAgentTools tools = mock(MusicAgentTools.class);
        AgentActionContext actions = new AgentActionContext();
        MusicAgentTurn turn = turn("播放 Mili 的歌");
        MusicTrackBo track = new MusicTrackBo("qq:1", "Iron Lotus", List.of("Mili"), "Millennium Mother",
                "https://img", 275_000, "https://source", "qq", "audio", "/api/music/qq/play/1", null);
        MusicRecommendationBo recommendation = new MusicRecommendationBo(
                turn.request(), "Mili", "找到歌曲", List.of("qq"), List.of(track));
        when(tools.recommendMusic(turn.request(), null, false)).thenAnswer(invocation -> {
            actions.add(AgentActionBo.showMusic(recommendation));
            return "找到一首歌曲";
        });
        when(tools.playRecommendedTrack(1)).thenReturn("Playback requested");
        var agent = new MusicExecutionAgent(tools, actions, new MusicAgentSessionStore(),
                new AgentToolAuthorizer(new AgentCapabilityRegistry()));
        actions.begin(new ConversationMemoryId(turn.userId(), turn.conversationId()));
        try {
            var result = agent.execute(turn, MusicAgentRoute.MUSIC_DISCOVERY);
            assertThat(result.successful()).isTrue();
            assertThat(result.factualAnswer()).contains("Iron Lotus", "Mili");
            verify(tools).recommendMusic(turn.request(), null, false);
            verify(tools).playRecommendedTrack(1);
        } finally {
            actions.clear();
        }
    }

    @Test
    void refreshFlagIsPassedToTheRecommendationDomainWithoutBecomingFeedback() {
        MusicAgentTools tools = mock(MusicAgentTools.class);
        AgentActionContext actions = new AgentActionContext();
        MusicAgentTurn turn = new MusicAgentTurn(3, UUID.randomUUID(), "根据反馈重新推荐一些歌", true);
        MusicTrackBo track = new MusicTrackBo("qq:new", "新歌", List.of("新歌手"), "新专辑",
                "", 180_000, "", "qq", "audio", "/api/music/qq/play/new", null);
        MusicRecommendationBo recommendation = new MusicRecommendationBo(
                turn.request(), "新推荐", "已排重", List.of("qq"), List.of(track));
        when(tools.recommendMusic(turn.request(), null, true)).thenAnswer(invocation -> {
            actions.add(AgentActionBo.showMusic(recommendation));
            return "找到一首新歌";
        });
        var agent = new MusicExecutionAgent(tools, actions, new MusicAgentSessionStore(),
                new AgentToolAuthorizer(new AgentCapabilityRegistry()));
        actions.begin(new ConversationMemoryId(turn.userId(), turn.conversationId()));
        try {
            assertThat(agent.execute(turn, MusicAgentRoute.MUSIC_DISCOVERY).successful()).isTrue();
            verify(tools).recommendMusic(turn.request(), null, true);
        } finally {
            actions.clear();
        }
    }

    private static MusicAgentTurn turn(String request) {
        return new MusicAgentTurn(3, UUID.randomUUID(), request);
    }
}
