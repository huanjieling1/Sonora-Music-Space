package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

@Component
public class LlmMusicSupportContextInterpreter implements MusicSupportContextInterpreter {
    private final AgentProperties properties;
    private final MultiAgentProperties multiAgentProperties;
    private volatile MusicSupportContextLanguageAgent agent;

    public LlmMusicSupportContextInterpreter(AgentProperties properties,
                                             MultiAgentProperties multiAgentProperties) {
        this.properties = properties;
        this.multiAgentProperties = multiAgentProperties;
    }

    @Override
    public Optional<MusicSupportContext> understand(String request) {
        if (!StringUtils.hasText(request) || !StringUtils.hasText(properties.apiKey())
                || !StringUtils.hasText(properties.modelName())) return Optional.empty();
        try {
            MusicSupportContext result = agent().understand(request.strip());
            return result == null || result.confidence() < 0.62 ? Optional.empty() : Optional.of(result);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private MusicSupportContextLanguageAgent agent() {
        MusicSupportContextLanguageAgent current = agent;
        if (current != null) return current;
        synchronized (this) {
            if (agent == null) {
                MultiAgentProperties.Role role = multiAgentProperties.intent();
                var builder = OpenAiChatModel.builder()
                        .apiKey(properties.apiKey()).modelName(role.modelOr(properties.modelName()))
                        .temperature(0.0).maxTokens(Math.min(role.maxTokens(), 800))
                        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .logRequests(properties.logRequests()).logResponses(properties.logResponses());
                if (StringUtils.hasText(properties.baseUrl())) builder.baseUrl(properties.baseUrl());
                agent = AiServices.builder(MusicSupportContextLanguageAgent.class)
                        .chatModel(builder.build()).build();
            }
            return agent;
        }
    }
}
