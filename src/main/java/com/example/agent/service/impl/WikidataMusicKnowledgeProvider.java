package com.example.agent.service.impl;

import com.example.agent.config.MusicKnowledgeProperties;
import com.example.agent.model.bo.MusicEntityType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
class WikidataMusicKnowledgeProvider implements PublicMusicKnowledgeProvider {
    private final MusicKnowledgeProperties properties;
    private final RestClient client;

    WikidataMusicKnowledgeProvider(MusicKnowledgeProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.resolvedTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        String baseUrl = properties.wikidata() == null || !StringUtils.hasText(properties.wikidata().baseUrl())
                ? "https://www.wikidata.org" : properties.wikidata().baseUrl();
        this.client = builder.baseUrl(trim(baseUrl)).requestFactory(requestFactory).build();
    }

    @Override
    public String id() {
        return "wikidata";
    }

    @Override
    public boolean enabled() {
        return properties.enabled() && properties.wikidata() != null && properties.wikidata().enabled();
    }

    @Override
    public Optional<ExternalMusicEntity> lookup(String candidate) {
        if (!enabled() || !StringUtils.hasText(candidate)) {
            return Optional.empty();
        }
        JsonNode response = client.get().uri(uri -> uri.path("/w/api.php")
                        .queryParam("action", "wbsearchentities")
                        .queryParam("search", candidate)
                        .queryParam("language", "zh")
                        .queryParam("uselang", "zh")
                        .queryParam("type", "item")
                        .queryParam("limit", 5)
                        .queryParam("format", "json")
                        .build())
                .retrieve().body(JsonNode.class);
        if (response == null || !response.path("search").isArray()) {
            return Optional.empty();
        }
        String expected = MusicTextNormalizer.normalize(candidate);
        ExternalMusicEntity best = null;
        double bestScore = 0;
        for (JsonNode item : response.path("search")) {
            String label = item.path("label").asText("");
            String description = item.path("description").asText("");
            String normalizedLabel = MusicTextNormalizer.normalize(label);
            double score = normalizedLabel.equals(expected) ? 0.92
                    : (normalizedLabel.contains(expected) || expected.contains(normalizedLabel)) ? 0.76 : 0;
            MusicEntityType type = classify(description);
            if (score <= bestScore || type == MusicEntityType.UNKNOWN) {
                continue;
            }
            LinkedHashSet<String> aliases = new LinkedHashSet<>();
            aliases.add(candidate);
            aliases.add(label);
            String matchText = item.path("match").path("text").asText("");
            if (StringUtils.hasText(matchText)) {
                aliases.add(matchText);
            }
            String conceptUrl = item.path("concepturi").asText("");
            best = new ExternalMusicEntity(label, type, List.copyOf(aliases), score,
                    id(), StringUtils.hasText(conceptUrl) ? conceptUrl : item.path("id").asText(""));
            bestScore = score;
        }
        return Optional.ofNullable(best);
    }

    private static MusicEntityType classify(String description) {
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (containsAny(text, "电子游戏", "video game", "computer game")) return MusicEntityType.GAME;
        if (containsAny(text, "锦标赛", "赛事", "tournament", "championship", "competition")) return MusicEntityType.EVENT;
        if (containsAny(text, "动画", "anime", "manga")) return MusicEntityType.ANIME;
        if (containsAny(text, "电影", "film", "movie", "television")) return MusicEntityType.FILM;
        if (containsAny(text, "原声", "soundtrack", "score")) return MusicEntityType.SOUNDTRACK;
        if (containsAny(text, "歌曲", "song", "single")) return MusicEntityType.TRACK;
        if (containsAny(text, "专辑", "album", "ep")) return MusicEntityType.ALBUM;
        if (containsAny(text, "歌手", "音乐家", "乐队", "singer", "musician", "band", "rapper")) return MusicEntityType.ARTIST;
        if (containsAny(text, "媒体系列", "franchise", "series")) return MusicEntityType.FRANCHISE;
        return MusicEntityType.UNKNOWN;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static String trim(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
