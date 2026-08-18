package com.example.agent.service.impl;

import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.service.MusicQueryPlanner;
import com.example.agent.service.MusicRecommendationAgent;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class GlmMusicQueryPlanner implements MusicQueryPlanner {
    private final AgentProperties properties;
    private final MultiAgentProperties multiAgentProperties;
    private final MusicTaxonomyService taxonomy;
    private volatile MusicRecommendationAgent agent;

    @Autowired
    public GlmMusicQueryPlanner(AgentProperties properties, MultiAgentProperties multiAgentProperties,
                                MusicTaxonomyService taxonomy) {
        this.properties = properties;
        this.multiAgentProperties = multiAgentProperties;
        this.taxonomy = taxonomy;
    }

    public GlmMusicQueryPlanner(AgentProperties properties, MusicTaxonomyService taxonomy) {
        this(properties, new MultiAgentProperties(true, null, null, null), taxonomy);
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
                MultiAgentProperties.Role role = multiAgentProperties.intent();
                var builder = OpenAiChatModel.builder()
                        .apiKey(properties.apiKey())
                        .baseUrl(properties.baseUrl())
                        .modelName(role.modelOr(properties.modelName()))
                        .temperature(Math.min(0.2, role.temperature()))
                        .maxTokens(role.maxTokens())
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
