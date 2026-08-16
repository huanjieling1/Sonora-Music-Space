package com.example.agent;

import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.AssistantAgent;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.tools.AgentActionContext;
import com.example.agent.tools.DevelopmentTools;
import com.example.agent.tools.MusicAgentTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "glm.music-agent-smoke-test", matches = "true")
class MusicAgentSmokeTest {
    @Test
    void mainAgentRoutesMusicAndPlaybackRequestsThroughTrustedTools() throws IOException {
        Properties environment = loadLocalEnvironment();
        String apiKey = environment.getProperty("AGENT_API_KEY", "").trim();
        assertThat(apiKey).as(".env 中的 AGENT_API_KEY").isNotBlank();

        MusicRecommendationService service = mock(MusicRecommendationService.class);
        MusicTrackBo track = new MusicTrackBo("qq:1", "Iron Lotus", List.of("Mili"), "Millennium Mother",
                "https://img", 275_000, "https://source", "qq", "audio", "/api/music/qq/play/1", null);
        when(service.recommend(any())).thenAnswer(invocation -> {
            var command = invocation.getArgument(0, com.example.agent.model.ao.MusicRecommendationAo.class);
            return new MusicRecommendationBo(command.description(), "energetic music", "找到 1 首歌曲",
                    List.of("qq"), List.of(track), command.page(), command.pageSize(),
                    command.page() < MusicRecommendationAo.MAX_PAGE, MusicRecommendationAo.MAX_PAGE);
        });

        AgentActionContext actionContext = new AgentActionContext();
        MusicAgentTools musicTools = new MusicAgentTools(service, mock(MusicPersonalizationService.class),
                new MusicAgentSessionStore(), actionContext);
        AssistantAgent assistant = AiServices.builder(AssistantAgent.class)
                .chatModel(model(environment, apiKey))
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder().id(id).maxMessages(12).build())
                .tools(new DevelopmentTools(), musicTools)
                .build();
        ConversationMemoryId memoryId = new ConversationMemoryId(
                1L, UUID.fromString("55555555-5555-4555-8555-555555555555"));

        actionContext.begin(memoryId);
        try {
            assistant.chat(memoryId, "请搜索并展示热血音乐列表，不要播放，也不要追问歌手。");
            assertThat(actionContext.actions()).extracting(action -> action.type())
                    .containsExactly(AgentActionType.SHOW_MUSIC_RESULTS);
        } finally {
            actionContext.clear();
        }

        actionContext.begin(memoryId);
        try {
            assistant.chat(memoryId, "播放第一首");
            assertThat(actionContext.actions()).extracting(action -> action.type())
                    .containsExactly(AgentActionType.PLAY_TRACK);
        } finally {
            actionContext.clear();
        }

        actionContext.begin(memoryId);
        try {
            assistant.chat(memoryId, "下一页");
            assertThat(actionContext.actions()).singleElement().satisfies(action -> {
                assertThat(action.type()).isEqualTo(AgentActionType.SHOW_MUSIC_RESULTS);
                assertThat(action.recommendation().page()).isEqualTo(2);
                assertThat(action.recommendation().pageSize()).isEqualTo(10);
            });
        } finally {
            actionContext.clear();
        }
    }

    private static OpenAiChatModel model(Properties environment, String apiKey) {
        return OpenAiChatModel.builder()
                .baseUrl(valueOrDefault(environment, "AGENT_BASE_URL", "https://open.bigmodel.cn/api/paas/v4"))
                .apiKey(apiKey)
                .modelName(valueOrDefault(environment, "AGENT_MODEL", "glm-4-flash-250414"))
                .temperature(0.0)
                .maxTokens(512)
                .timeout(Duration.ofSeconds(60))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private static Properties loadLocalEnvironment() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static String valueOrDefault(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key, "").trim();
        return value.isEmpty() ? fallback : value;
    }
}
