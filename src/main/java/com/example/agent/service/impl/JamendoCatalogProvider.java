package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.service.MusicCatalogProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class JamendoCatalogProvider implements MusicCatalogProvider {
    private static final String PROVIDER = "jamendo";

    private final MusicCatalogProperties.Jamendo configuration;
    private final RestClient restClient;

    @Autowired
    public JamendoCatalogProvider(MusicCatalogProperties properties, RestClient.Builder builder) {
        this(properties, createClient(properties, builder));
    }

    JamendoCatalogProvider(MusicCatalogProperties properties, RestClient restClient) {
        this.configuration = properties.jamendo();
        this.restClient = restClient;
    }

    @Override
    public String id() {
        return PROVIDER;
    }

    @Override
    public String displayName() {
        return "Jamendo";
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
        return 10;
    }

    @Override
    public List<String> playbackTypes() {
        return List.of("audio");
    }

    @Override
    public List<MusicTrackBo> search(String query, int limit) {
        return search(new MusicSearchTask(MusicSearchTaskType.KEYWORDS, query, null, null, null), 1, limit);
    }

    @Override
    public List<MusicTrackBo> search(MusicSearchTask task, int limit) {
        return search(task, 1, limit);
    }

    @Override
    public List<MusicTrackBo> search(MusicSearchTask task, int page, int pageSize) {
        if (!configured()) {
            return List.of();
        }
        JsonNode response = restClient.get()
                .uri(uri -> {
                    UriBuilder request = uri.path("/tracks/")
                            .queryParam("client_id", configuration.clientId())
                            .queryParam("format", "json")
                            .queryParam("limit", pageSize)
                            .queryParam("offset", (page - 1) * pageSize)
                            .queryParam("type", "single albumtrack")
                            .queryParam("audioformat", "mp32")
                            .queryParam("imagesize", 300)
                            .queryParam("include", "licenses musicinfo");
                    addSearchParameters(request, task);
                    return request.build();
                })
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("results").isArray()) {
            return List.of();
        }
        List<MusicTrackBo> tracks = new ArrayList<>();
        for (JsonNode item : response.path("results")) {
            String id = item.path("id").asText("");
            String name = item.path("name").asText("");
            String audio = item.path("audio").asText("");
            if (!StringUtils.hasText(id) || !StringUtils.hasText(name) || !isHttps(audio)) {
                continue;
            }
            String artist = item.path("artist_name").asText("");
            String image = firstText(item, "image", "album_image");
            tracks.add(new MusicTrackBo(
                    PROVIDER + ":" + id,
                    name,
                    StringUtils.hasText(artist) ? List.of(artist) : List.of(),
                    item.path("album_name").asText(""),
                    image,
                    Math.max(0, item.path("duration").asLong()) * 1000,
                    nullableText(item, "shareurl"),
                    PROVIDER,
                    "audio",
                    audio,
                    nullableText(item, "license_ccurl")
            ));
        }
        return tracks;
    }

    private static void addSearchParameters(UriBuilder request, MusicSearchTask task) {
        switch (task.type()) {
            case TRACK -> request.queryParam("namesearch", firstText(task.track(), task.query()));
            case TRACK_ARTIST -> {
                request.queryParam("namesearch", firstText(task.track(), task.query()));
                if (StringUtils.hasText(task.artist())) {
                    request.queryParam("artist_name", task.artist());
                }
            }
            case ARTIST -> request.queryParam("artist_name", firstText(task.artist(), task.query()));
            case ALBUM -> {
                request.queryParam("album_name", firstText(task.album(), task.query()));
                if (StringUtils.hasText(task.artist())) {
                    request.queryParam("artist_name", task.artist());
                }
            }
            case SIMILAR -> request.queryParam(StringUtils.hasText(task.artist()) ? "xartist" : "search",
                    firstText(task.artist(), task.query()));
            default -> request.queryParam("search", task.query());
        }
    }

    private static String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static RestClient createClient(MusicCatalogProperties properties, RestClient.Builder builder) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.resolvedTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return builder.baseUrl(trimTrailingSlash(properties.jamendo().baseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isHttps(String value) {
        return StringUtils.hasText(value) && value.startsWith("https://");
    }

    private static String firstText(JsonNode item, String... names) {
        for (String name : names) {
            String value = item.path(name).asText("");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static String nullableText(JsonNode item, String name) {
        String value = item.path(name).asText("");
        return StringUtils.hasText(value) ? value : null;
    }
}
