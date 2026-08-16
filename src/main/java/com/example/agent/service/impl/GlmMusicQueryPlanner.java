package com.example.agent.service.impl;

import com.example.agent.config.AgentProperties;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.service.MusicQueryPlanner;
import com.example.agent.service.MusicRecommendationAgent;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class GlmMusicQueryPlanner implements MusicQueryPlanner {
    private final AgentProperties properties;
    private final MusicTaxonomyService taxonomy;
    private volatile MusicRecommendationAgent agent;

    public GlmMusicQueryPlanner(AgentProperties properties, MusicTaxonomyService taxonomy) {
        this.properties = properties;
        this.taxonomy = taxonomy;
    }

    @Override
    public MusicSearchPlan plan(String description) {
        MusicSearchPlan fallback = taxonomy.fallbackPlan(description);
        if (!org.springframework.util.StringUtils.hasText(properties.apiKey())
                || !org.springframework.util.StringUtils.hasText(properties.baseUrl())
                || !org.springframework.util.StringUtils.hasText(properties.modelName())) {
            return fallback;
        }
        try {
            return taxonomy.enrich(agent().createSearchPlan(description), description);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private MusicRecommendationAgent agent() {
        MusicRecommendationAgent current = agent;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (agent == null) {
                var builder = OpenAiChatModel.builder()
                        .apiKey(properties.apiKey())
                        .baseUrl(properties.baseUrl())
                        .modelName(properties.modelName())
                        .temperature(0.0)
                        .maxTokens(Math.min(800, Math.max(300, properties.maxTokens())))
                        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .logRequests(properties.logRequests())
                        .logResponses(properties.logResponses());
                agent = AiServices.builder(MusicRecommendationAgent.class)
                        .chatModel(builder.build())
                        .build();
            }
            return agent;
        }
    }
}
