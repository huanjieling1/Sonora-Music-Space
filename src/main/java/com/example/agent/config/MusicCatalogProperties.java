package com.example.agent.config;

import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "music.catalog")
public record MusicCatalogProperties(
        int timeoutSeconds,
        Jamendo jamendo,
        Audius audius,
        Youtube youtube,
        Qq qq
) {
    @ConstructorBinding
    public MusicCatalogProperties {
    }

    public MusicCatalogProperties(int timeoutSeconds, Jamendo jamendo, Audius audius, Youtube youtube) {
        this(timeoutSeconds, jamendo, audius, youtube,
                new Qq(false, "http://127.0.0.1:3200", "runtime-data", "flac"));
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds > 0 ? timeoutSeconds : 5;
    }

    public record Jamendo(String clientId, String baseUrl) {
        public boolean configured() {
            return StringUtils.hasText(clientId) && StringUtils.hasText(baseUrl);
        }
    }

    public record Audius(String apiKey, String baseUrl) {
        public boolean configured() {
            return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
        }
    }

    public record Youtube(String apiKey, String baseUrl) {
        public boolean configured() {
            return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
        }
    }

    public record Qq(boolean enabled, String baseUrl, String sessionDirectory, String defaultQuality) {
        public boolean configured() {
            return enabled && StringUtils.hasText(baseUrl) && StringUtils.hasText(sessionDirectory);
        }
    }
}
