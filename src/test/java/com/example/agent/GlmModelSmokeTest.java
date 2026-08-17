package com.example.agent;

import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.service.AssistantAgent;
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
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "glm.smoke-test", matches = "true")
class GlmModelSmokeTest {
    private static final String GLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String GLM_MODEL = "glm-4-flash-250414";

    @Test
    void callsGlmThroughLangChain4j() throws IOException {
        Properties environment = loadLocalEnvironment();
        String apiKey = environment.getProperty("AGENT_API_KEY", "").trim();
        assertThat(apiKey).as(".env 中的 AGENT_API_KEY").isNotBlank();

        var model = OpenAiChatModel.builder()
                .baseUrl(valueOrDefault(environment, "AGENT_BASE_URL", GLM_BASE_URL))
                .apiKey(apiKey)
                .modelName(valueOrDefault(environment, "AGENT_MODEL", GLM_MODEL))
                .temperature(0.0)
                .maxTokens(32)
                .timeout(Duration.ofSeconds(60))
                .logRequests(false)
                .logResponses(false)
                .build();

        AssistantAgent assistant = AiServices.builder(AssistantAgent.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(4)
                        .build())
                .build();

        String answer = assistant.chat(
                new ConversationMemoryId(1L, UUID.fromString("11111111-1111-4111-8111-111111111111")),
                "只回复：GLM连接成功");

        assertThat(answer).contains("GLM连接成功");
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
