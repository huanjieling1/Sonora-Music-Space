package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public record AgentProperties(
        String apiKey,
        String baseUrl,
        String modelName,
        double temperature,
        int maxTokens,
        int memoryMaxMessages,
        int timeoutSeconds,
        boolean logRequests,
        boolean logResponses
) {
}
