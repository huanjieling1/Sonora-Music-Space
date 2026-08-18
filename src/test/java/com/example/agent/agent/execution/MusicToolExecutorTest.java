package com.example.agent.agent.execution;

import com.example.agent.agent.capability.AgentToolAuthorizer;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.tools.AgentActionContext;
import com.example.agent.tools.MusicAgentTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MusicToolExecutorTest {
    private final MusicAgentTools tools = mock(MusicAgentTools.class);
    private final AgentActionContext actionContext = new AgentActionContext();
    private final AgentToolAuthorizer authorizer = mock(AgentToolAuthorizer.class);
    private final MusicToolExecutor executor = new MusicToolExecutor(
            tools, actionContext, new MusicAgentSessionStore(), authorizer);

    @BeforeEach
    void begin() {
        actionContext.begin(new ConversationMemoryId(7L, UUID.randomUUID()));
        doNothing().when(authorizer).requireAllowed(
                com.example.agent.agent.capability.AgentRole.EXECUTION,
                "playRandomQqPublicPlaylist");
    }

    @AfterEach
    void clear() {
        actionContext.clear();
    }

    @Test
    void classifiesLoadedQueueWithoutPlaybackAsPartialSuccess() {
        MusicTrackBo track = new MusicTrackBo("qq:test1", "Test", List.of("Artist"), "Album",
                "https://img", 180_000, "https://source", "qq", "audio",
                "/api/music/qq/play/test1", null);
        MusicRecommendationBo recommendation = new MusicRecommendationBo(
                "随机歌单", "随机歌单", "队列已保留", List.of("qq"), List.of(track));
        when(tools.playRandomQqPublicPlaylist()).thenAnswer(invocation -> {
            actionContext.add(AgentActionBo.showMusic(recommendation));
            actionContext.add(AgentActionBo.queueMusic(recommendation));
            return "歌单已经加载，当前账号未找到可播放曲目，队列已保留";
        });

        var result = executor.randomPlaylist(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST);

        assertThat(result.successful()).isTrue();
        assertThat(result.partial()).isTrue();
        assertThat(result.evidenceTypes()).containsExactlyInAnyOrder(
                com.example.agent.model.bo.AgentActionType.SHOW_MUSIC_RESULTS,
                com.example.agent.model.bo.AgentActionType.QUEUE_MUSIC_RESULTS);
    }
}
