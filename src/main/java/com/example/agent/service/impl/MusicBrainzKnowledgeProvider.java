package com.example.agent.service.impl;

import com.example.agent.config.MusicKnowledgeProperties;
import com.example.agent.model.bo.MusicEntityType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Component
class MusicBrainzKnowledgeProvider implements PublicMusicKnowledgeProvider {
    private static final AtomicLong NEXT_REQUEST_AT = new AtomicLong();

    private final MusicKnowledgeProperties properties;
    private final RestClient client;

    MusicBrainzKnowledgeProvider(MusicKnowledgeProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.resolvedTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        String baseUrl = properties.musicbrainz() == null || !StringUtils.hasText(properties.musicbrainz().baseUrl())
                ? "https://musicbrainz.org" : properties.musicbrainz().baseUrl();
        String userAgent = properties.musicbrainz() == null || !StringUtils.hasText(properties.musicbrainz().userAgent())
                ? "Sonora/1.0 (personal music assistant)" : properties.musicbrainz().userAgent();
        this.client = builder.baseUrl(trim(baseUrl)).requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent).build();
    }

    @Override
    public String id() {
        return "musicbrainz";
    }

    @Override
    public boolean enabled() {
        return properties.enabled() && properties.musicbrainz() != null && properties.musicbrainz().enabled();
    }

    @Override
    public Optional<ExternalMusicEntity> lookup(String candidate) {
        if (!enabled() || !StringUtils.hasText(candidate)) {
            return Optional.empty();
        }
        obeyRateLimit();
        JsonNode response = client.get().uri(uri -> uri.path("/ws/2/recording")
                        .queryParam("query", "recording:\"" + candidate + "\"")
                        .queryParam("fmt", "json")
                        .queryParam("limit", 3)
                        .build())
                .retrieve().body(JsonNode.class);
        if (response == null || !response.path("recordings").isArray()) {
            return Optional.empty();
        }
        String expected = MusicTextNormalizer.normalize(candidate);
        for (JsonNode item : response.path("recordings")) {
            String title = item.path("title").asText("");
            if (!MusicTextNormalizer.normalize(title).equals(expected)) {
                continue;
            }
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            aliases.add(candidate);
            aliases.add(title);
            if (item.path("artist-credit").isArray()) {
                item.path("artist-credit").forEach(credit -> {
                    String artist = credit.path("name").asText("");
                    if (StringUtils.hasText(artist)) aliases.add(artist);
                });
            }
            return Optional.of(new ExternalMusicEntity(title, MusicEntityType.TRACK,
                    List.copyOf(aliases), 0.9, id(), item.path("id").asText("")));
        }
        return Optional.empty();
    }

    private static synchronized void obeyRateLimit() {
        long now = System.currentTimeMillis();
        long wait = Math.max(0, NEXT_REQUEST_AT.get() - now);
        if (wait > 0) {
            try {
                Thread.sleep(Math.min(wait, 1000));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        NEXT_REQUEST_AT.set(System.currentTimeMillis() + 1000);
    }

    private static String trim(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
