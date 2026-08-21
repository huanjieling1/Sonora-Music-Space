package com.example.agent.agent.goal;

import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;

/** Zero-temperature structured goal decomposer. Any model failure falls back to deterministic Java parsing. */
@Component
public final class LlmMusicGoalDraftInterpreter implements MusicGoalDraftInterpreter {
    private final AgentProperties properties;
    private final MultiAgentProperties multiAgentProperties;
    private volatile MusicGoalDecompositionLanguageAgent agent;

    public LlmMusicGoalDraftInterpreter(AgentProperties properties,
                                        MultiAgentProperties multiAgentProperties) {
        this.properties = properties;
        this.multiAgentProperties = multiAgentProperties;
    }

    @Override
    public Optional<UserGoalGraph> decompose(String request) {
        if (!StringUtils.hasText(request) || !StringUtils.hasText(properties.apiKey())
                || !StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.modelName())) {
            return Optional.empty();
        }
        try {
            UserGoalGraph graph = agent().decompose(request.strip());
            return graph == null || graph.goals().isEmpty() || graph.goals().size() > 12
                    ? Optional.empty() : Optional.of(graph);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private MusicGoalDecompositionLanguageAgent agent() {
        MusicGoalDecompositionLanguageAgent current = agent;
        if (current != null) return current;
        synchronized (this) {
            if (agent == null) {
                MultiAgentProperties.Role role = multiAgentProperties.intent();
                var model = OpenAiChatModel.builder()
                        .apiKey(properties.apiKey()).baseUrl(properties.baseUrl())
                        .modelName(role.modelOr(properties.modelName()))
                        .temperature(0.0).maxTokens(Math.max(1200, role.maxTokens()))
                        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .logRequests(properties.logRequests()).logResponses(properties.logResponses()).build();
                agent = AiServices.builder(MusicGoalDecompositionLanguageAgent.class)
                        .chatModel(model).build();
            }
            return agent;
        }
    }
}
