package com.example.agent.service.impl;

import com.example.agent.model.ao.ChatAo;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.bo.AgentReplyBo;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.QqMusicSearchBo;
import com.example.agent.model.bo.QqPlaylistSearchResultBo;
import com.example.agent.model.bo.QqArtistSearchResultBo;
import com.example.agent.model.entity.AgentChatMessage;
import com.example.agent.model.entity.AgentConversation;
import com.example.agent.service.AgentChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {
    @Mock ConversationStore store;
    @Mock AgentChatService agentChatService;

    private ObjectMapper objectMapper;
    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ConversationServiceImpl(store, agentChatService, objectMapper);
    }

    @Test
    void restoresPersistedMusicCardsFromConversationHistory() throws Exception {
        UUID conversationId = UUID.randomUUID();
        AgentActionBo card = AgentActionBo.showMusic(recommendation());
        String actionsJson = objectMapper.writeValueAsString(List.of(card));
        AgentChatMessage assistant = AgentChatMessage.assistant(
                conversationId.toString(), "已找到音乐", actionsJson, LocalDateTime.now());
        when(store.history(7L, conversationId)).thenReturn(List.of(assistant));

        var history = service.history(7L, conversationId);

        assertThat(history).singleElement().satisfies(message -> {
            assertThat(message.content()).isEqualTo("已找到音乐");
            assertThat(message.actions()).singleElement().satisfies(action -> {
                assertThat(action.type()).isEqualTo(AgentActionType.SHOW_MUSIC_RESULTS);
                assertThat(action.recommendation().tracks()).singleElement()
                        .satisfies(track -> assertThat(track.name()).isEqualTo("Iron Lotus"));
            });
        });
    }

    @Test
    void persistsDisplayCardsWithoutReplayingPlayerCommands() throws Exception {
        UUID conversationId = UUID.randomUUID();
        var recommendation = recommendation();
        var track = recommendation.tracks().get(0);
        var actions = List.of(
                AgentActionBo.showMusic(recommendation),
                AgentActionBo.showQqPlaylists(playlistSearch()),
                AgentActionBo.showQqArtists(artistSearch()),
                AgentActionBo.playTrack(track),
                AgentActionBo.queueMusic(recommendation));
        when(agentChatService.chat(7L, conversationId, "播放 Mili"))
                .thenReturn(new AgentReplyBo("开始播放", actions));
        when(store.saveExchange(eq(7L), eq(conversationId), eq("播放 Mili"), eq("开始播放"), anyString()))
                .thenReturn(AgentConversation.create(7L));

        service.chat(new ChatAo(7L, conversationId, "播放 Mili"));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(store).saveExchange(eq(7L), eq(conversationId), eq("播放 Mili"), eq("开始播放"), json.capture());
        AgentActionBo[] persisted = objectMapper.readValue(json.getValue(), AgentActionBo[].class);
        assertThat(persisted).extracting(AgentActionBo::type)
                .containsExactly(AgentActionType.SHOW_MUSIC_RESULTS, AgentActionType.SHOW_QQ_PLAYLIST_RESULTS,
                        AgentActionType.SHOW_QQ_ARTIST_RESULTS);
        assertThat(persisted[1].playlistSearch().playlists()).singleElement()
                .satisfies(playlist -> assertThat(playlist.name()).isEqualTo("Mili 精选歌单"));
        assertThat(persisted[2].artistSearch().artists()).singleElement()
                .satisfies(artist -> assertThat(artist.name()).isEqualTo("Mili"));
    }

    private static MusicRecommendationBo recommendation() {
        MusicTrackBo track = new MusicTrackBo(
                "qq:1", "Iron Lotus", List.of("Mili"), "Millennium Mother",
                "https://img", 180_000, "https://source", "qq", "audio",
                "/api/music/qq/play/1", null);
        return new MusicRecommendationBo(
                "Mili", "Mili", "找到 1 首歌曲", List.of("qq"), List.of(track));
    }

    private static QqPlaylistSearchResultBo playlistSearch() {
        var playlist = new QqMusicSearchBo.Playlist(
                "7123456789", "Mili 精选歌单", "公开歌单", "https://playlist-cover",
                "QQ 用户", 20_000, 24, "https://y.qq.com/n/ryqq/playlist/7123456789");
        return new QqPlaylistSearchResultBo(null, "Mili", 1, 12, 1, false, List.of(playlist));
    }

    private static QqArtistSearchResultBo artistSearch() {
        var artist = new QqArtistSearchResultBo.ArtistProfile(
                "0030xQJo2D8d6H", "Mili", "https://artist", "", "2012", "日本", "艺人简介",
                "https://y.qq.com/n/ryqq/singer/0030xQJo2D8d6H", 1, 1, 0, false, false,
                List.of(recommendation().tracks().get(0)), List.of(), "简介摘要", "目录成就摘要", "曲风摘要");
        return new QqArtistSearchResultBo(null, "Mili", 1, 5, 1, false, List.of(artist));
    }
}
