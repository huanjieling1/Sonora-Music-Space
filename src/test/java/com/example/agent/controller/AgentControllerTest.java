package com.example.agent.controller;

import com.example.agent.model.ao.ChatAo;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.ChatResultBo;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.dto.agent.ChatRequest;
import com.example.agent.security.AppUserPrincipal;
import com.example.agent.service.ConversationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControllerTest {
    @Test
    void forwardsAuthenticatedUserAndConversationToChatMemory() {
        ConversationService conversationService = mock(ConversationService.class);
        AgentController controller = new AgentController(conversationService);
        AppUserPrincipal user = new AppUserPrincipal(42L, "developer", "dev@example.com",
                "13812345678", "password-hash");
        UUID conversationId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        MusicTrackBo track = new MusicTrackBo("qq:1", "Iron Lotus", List.of("Mili"), "Album",
                "https://img", 180_000, "https://source", "qq", "audio", "/api/music/qq/play/1", null);
        MusicRecommendationBo recommendation = new MusicRecommendationBo(
                "Mili", "Mili", "找到 1 首歌曲", List.of("qq"), List.of(track));
        when(conversationService.chat(new ChatAo(42L, conversationId, "hello")))
                .thenReturn(new ChatResultBo(conversationId, "world", List.of(AgentActionBo.showMusic(recommendation))));

        var response = controller.chat(new ChatRequest(conversationId, " hello "), user);

        assertThat(response.data().conversationId()).isEqualTo(conversationId);
        assertThat(response.data().answer()).isEqualTo("world");
        assertThat(response.data().actions()).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo("SHOW_MUSIC_RESULTS");
            assertThat(action.recommendation().tracks()).singleElement()
                    .satisfies(resultTrack -> assertThat(resultTrack.name()).isEqualTo("Iron Lotus"));
        });
        verify(conversationService).chat(new ChatAo(42L, conversationId, "hello"));
    }
}
