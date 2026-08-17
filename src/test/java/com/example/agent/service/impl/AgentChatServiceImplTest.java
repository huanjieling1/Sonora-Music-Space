package com.example.agent.service.impl;

import com.example.agent.config.AgentProperties;
import com.example.agent.skill.AgentSkillRegistry;
import com.example.agent.tools.AgentActionContext;
import com.example.agent.tools.MusicAgentTools;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentChatServiceImplTest {
    @Test
    void routesShortRandomPlaylistCommandDirectlyToQqPublicPlaylistTool() {
        MusicAgentTools musicAgentTools = mock(MusicAgentTools.class);
        org.mockito.Mockito.when(musicAgentTools.playRandomQqPublicPlaylist())
                .thenReturn("已随机选择 QQ 音乐公开歌单并开始播放。");
        var service = new AgentChatServiceImpl(
                new AgentProperties("test-key", "http://localhost", "test-model",
                        0.0, 1024, 20, 30, false, false),
                mock(ConversationStore.class),
                musicAgentTools,
                new AgentActionContext(),
                mock(AgentSkillRegistry.class));

        var reply = service.chat(1L, UUID.randomUUID(), "随机歌单给我");

        assertThat(reply.answer()).isEqualTo("已随机选择 QQ 音乐公开歌单并开始播放。");
        verify(musicAgentTools).playRandomQqPublicPlaylist();
    }

    @Test
    void routesNamedPlaylistSearchDirectlyToDedicatedQqTool() {
        String request = "找一个跟无畏契约相关的歌单给我";
        MusicAgentTools musicAgentTools = mock(MusicAgentTools.class);
        when(musicAgentTools.searchQqPlaylists(request)).thenReturn("已展示 3 个 QQ 音乐公开歌单卡片。");
        var service = new AgentChatServiceImpl(
                new AgentProperties("test-key", "http://localhost", "test-model",
                        0.0, 1024, 20, 30, false, false),
                mock(ConversationStore.class),
                musicAgentTools,
                new AgentActionContext(),
                mock(AgentSkillRegistry.class));

        var reply = service.chat(1L, UUID.randomUUID(), request);

        assertThat(reply.answer()).isEqualTo("已展示 3 个 QQ 音乐公开歌单卡片。");
        verify(musicAgentTools).searchQqPlaylists(request);
    }

    @Test
    void routesArtistProfileLookupDirectlyToDedicatedArtistTool() {
        String request = "找歌手 Mili 并介绍她们";
        MusicAgentTools musicAgentTools = mock(MusicAgentTools.class);
        when(musicAgentTools.searchQqArtists(request)).thenReturn("已展示 Mili 的大型艺人档案卡。");
        var service = new AgentChatServiceImpl(
                new AgentProperties("test-key", "http://localhost", "test-model",
                        0.0, 1024, 20, 30, false, false),
                mock(ConversationStore.class), musicAgentTools, new AgentActionContext(),
                mock(AgentSkillRegistry.class));

        var reply = service.chat(1L, UUID.randomUUID(), request);

        assertThat(reply.answer()).isEqualTo("已展示 Mili 的大型艺人档案卡。");
        verify(musicAgentTools).searchQqArtists(request);
    }
}
