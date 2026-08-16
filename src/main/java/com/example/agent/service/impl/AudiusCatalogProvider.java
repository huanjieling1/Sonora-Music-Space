package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.MusicCatalogProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AudiusCatalogProvider implements MusicCatalogProvider {
    private static final String PROVIDER = "audius";

    private final MusicCatalogProperties.Audius configuration;
    private final String baseUrl;
    private final RestClient restClient;

    @Autowired
    public AudiusCatalogProvider(MusicCatalogProperties properties, RestClient.Builder builder) {
        this(properties, createClient(properties, builder));
    }

    AudiusCatalogProvider(MusicCatalogProperties properties, RestClient restClient) {
        this.configuration = properties.audius();
        this.baseUrl = trimTrailingSlash(configuration.baseUrl());
        this.restClient = restClient;
    }

    @Override
    public String id() {
        return PROVIDER;
    }

    @Override
    public String displayName() {
        return "Audius";
    }

    @Override
    public boolean configured() {
        return configuration != null && configuration.configured();
    }

    @Override
    public boolean fallbackOnly() {
        return false;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<String> playbackTypes() {
        return List.of("audio");
    }

    @Override
    public List<MusicTrackBo> search(String query, int limit) {
        return search(query, 1, limit);
    }

    @Override
    public List<MusicTrackBo> search(String query, int page, int pageSize) {
        if (!configured()) {
            return List.of();
        }
        JsonNode response = restClient.get()
                .uri(uri -> uri.path("/tracks/search")
                        .queryParam("query", query)
                        .queryParam("limit", pageSize)
                        .queryParam("offset", (page - 1) * pageSize)
                        .queryParam("sort_method", "relevant")
                        .build())
                .headers(headers -> headers.setBearerAuth(configuration.apiKey()))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("data").isArray()) {
            return List.of();
        }
        List<MusicTrackBo> tracks = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            String id = item.path("id").asText("");
            String name = item.path("title").asText("");
            boolean gated = item.path("is_stream_gated").asBoolean(false);
            boolean available = !item.has("is_available") || item.path("is_available").asBoolean(true);
            boolean streamable = !item.has("stream") || item.path("stream").asBoolean(true);
            if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || gated || !available || !streamable) {
                continue;
            }
            String artist = item.path("user").path("name").asText("");
            String permalink = item.path("permalink").asText("");
            String externalUrl = StringUtils.hasText(permalink)
                    ? (permalink.startsWith("http") ? permalink : "https://audius.co/" + permalink.replaceFirst("^/", ""))
                    : null;
            String playbackUrl = UriComponentsBuilder.fromUriString(baseUrl)
                    .path("/tracks/").pathSegment(id).path("/stream")
                    .build().encode().toUriString();
            tracks.add(new MusicTrackBo(
                    PROVIDER + ":" + id,
                    name,
                    StringUtils.hasText(artist) ? List.of(artist) : List.of(),
                    item.path("album_backlink").path("playlist_name").asText(""),
                    artwork(item),
                    Math.max(0, item.path("duration").asLong()) * 1000,
                    externalUrl,
                    PROVIDER,
                    "audio",
                    playbackUrl,
                    null
            ));
        }
        return tracks;
    }

    private static RestClient createClient(MusicCatalogProperties properties, RestClient.Builder builder) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.resolvedTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return builder.baseUrl(trimTrailingSlash(properties.audius().baseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private static String artwork(JsonNode item) {
        for (String size : List.of("480x480", "1000x1000", "150x150")) {
            String value = item.path("artwork").path(size).asText("");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
