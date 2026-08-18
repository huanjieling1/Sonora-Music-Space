package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

/** Zero-temperature semantic parser. Invalid or unavailable model output falls back to Java rules. */
@Component
public class LlmMusicSemanticIntentInterpreter implements MusicSemanticIntentInterpreter {
    private final AgentProperties properties;
    private final MultiAgentProperties multiAgentProperties;
    private volatile MusicSemanticIntentLanguageAgent agent;

    public LlmMusicSemanticIntentInterpreter(AgentProperties properties,
                                              MultiAgentProperties multiAgentProperties) {
        this.properties = properties;
        this.multiAgentProperties = multiAgentProperties;
    }

    @Override
    public Optional<MusicIntentDraft> understand(String request) {
        if (!StringUtils.hasText(request) || !StringUtils.hasText(properties.apiKey())
                || !StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.modelName())) {
            return Optional.empty();
        }
        try {
            MusicIntentDraft value = agent().understand(request.strip());
            return value == null || value.confidence() < 0.55 ? Optional.empty() : Optional.of(value);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private MusicSemanticIntentLanguageAgent agent() {
        MusicSemanticIntentLanguageAgent current = agent;
        if (current != null) return current;
        synchronized (this) {
            if (agent == null) {
                MultiAgentProperties.Role role = multiAgentProperties.intent();
                var model = OpenAiChatModel.builder()
                        .apiKey(properties.apiKey()).baseUrl(properties.baseUrl())
                        .modelName(role.modelOr(properties.modelName()))
                        .temperature(0.0).maxTokens(role.maxTokens())
                        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .logRequests(properties.logRequests()).logResponses(properties.logResponses()).build();
                agent = AiServices.builder(MusicSemanticIntentLanguageAgent.class).chatModel(model).build();
            }
            return agent;
        }
    }
}
