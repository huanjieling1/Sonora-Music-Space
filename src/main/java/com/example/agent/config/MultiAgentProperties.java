package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Role-specific model settings. API credentials remain in {@link AgentProperties}. */
@ConfigurationProperties(prefix = "agent.multi-agent")
public record MultiAgentProperties(
        boolean enabled,
        Role profile,
        Role intent,
        Role conversation
) {
    public MultiAgentProperties {
        profile = profile == null ? new Role(null, 0.55, 1600) : profile.normalized(0.55, 1600);
        intent = intent == null ? new Role(null, 0.0, 800) : intent.normalized(0.0, 800);
        conversation = conversation == null ? new Role(null, 0.2, 4096) : conversation.normalized(0.2, 4096);
    }

    public record Role(String modelName, double temperature, int maxTokens) {
        private Role normalized(double fallbackTemperature, int fallbackMaxTokens) {
            double safeTemperature = Double.isFinite(temperature)
                    ? Math.max(0, Math.min(1, temperature)) : fallbackTemperature;
            int safeMaxTokens = maxTokens > 0 ? Math.min(maxTokens, 8192) : fallbackMaxTokens;
            return new Role(StringUtils.hasText(modelName) ? modelName.strip() : null,
                    safeTemperature, safeMaxTokens);
        }

        public String modelOr(String fallback) {
            return StringUtils.hasText(modelName) ? modelName : fallback;
        }
    }
}
