package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "music.knowledge")
public record MusicKnowledgeProperties(
        boolean enabled,
        int timeoutSeconds,
        int successCacheDays,
        int failureCacheDays,
        Wikidata wikidata,
        MusicBrainz musicbrainz
) {
    @ConstructorBinding
    public MusicKnowledgeProperties {
    }

    public int resolvedTimeoutSeconds() {
        return timeoutSeconds > 0 ? timeoutSeconds : 2;
    }

    public int resolvedSuccessCacheDays() {
        return successCacheDays > 0 ? successCacheDays : 30;
    }

    public int resolvedFailureCacheDays() {
        return failureCacheDays > 0 ? failureCacheDays : 1;
    }

    public record Wikidata(boolean enabled, String baseUrl) {
    }

    public record MusicBrainz(boolean enabled, String baseUrl, String userAgent) {
    }
}
