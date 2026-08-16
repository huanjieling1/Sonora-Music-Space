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
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class YoutubeCatalogProvider implements MusicCatalogProvider {
    private static final String PROVIDER = "youtube";

    private final MusicCatalogProperties.Youtube configuration;
    private final RestClient restClient;
    private final Map<String, Map<Integer, String>> pageTokens = new ConcurrentHashMap<>();

    @Autowired
    public YoutubeCatalogProvider(MusicCatalogProperties properties, RestClient.Builder builder) {
        this(properties, createClient(properties, builder));
    }

    YoutubeCatalogProvider(MusicCatalogProperties properties, RestClient restClient) {
        this.configuration = properties.youtube();
        this.restClient = restClient;
    }

    @Override
    public String id() {
        return PROVIDER;
    }

    @Override
    public String displayName() {
        return "YouTube";
    }

    @Override
    public boolean configured() {
        return configuration != null && configuration.configured();
    }

    @Override
    public boolean fallbackOnly() {
        return true;
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public List<String> playbackTypes() {
        return List.of("youtube");
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
        String tokenKey = query + "\u0000" + pageSize;
        Map<Integer, String> tokens = pageTokens.computeIfAbsent(tokenKey, ignored -> new ConcurrentHashMap<>());
        if (page == 1) {
            tokens.clear();
        }
        String pageToken = page == 1 ? null : tokens.get(page);
        if (page > 1 && !StringUtils.hasText(pageToken)) {
            return List.of();
        }
        JsonNode response = restClient.get()
                .uri(uri -> {
                    var request = uri.path("/search")
                            .queryParam("key", configuration.apiKey())
                            .queryParam("part", "snippet")
                            .queryParam("type", "video")
                            .queryParam("videoCategoryId", "10")
                            .queryParam("videoEmbeddable", "true")
                            .queryParam("videoSyndicated", "true")
                            .queryParam("maxResults", Math.min(50, pageSize))
                            .queryParam("q", query);
                    if (StringUtils.hasText(pageToken)) {
                        request.queryParam("pageToken", pageToken);
                    }
                    return request.build();
                })
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.path("items").isArray()) {
            return List.of();
        }
        String nextPageToken = response.path("nextPageToken").asText("");
        if (StringUtils.hasText(nextPageToken) && page < 20) {
            tokens.put(page + 1, nextPageToken);
        } else {
            tokens.remove(page + 1);
        }
        if (pageTokens.size() > 100) {
            pageTokens.clear();
        }
        List<MusicTrackBo> tracks = new ArrayList<>();
        for (JsonNode item : response.path("items")) {
            String videoId = item.path("id").path("videoId").asText("");
            String title = HtmlUtils.htmlUnescape(item.path("snippet").path("title").asText(""));
            if (!StringUtils.hasText(videoId) || !StringUtils.hasText(title)) {
                continue;
            }
            String channel = HtmlUtils.htmlUnescape(item.path("snippet").path("channelTitle").asText(""));
            tracks.add(new MusicTrackBo(
                    PROVIDER + ":" + videoId,
                    title,
                    StringUtils.hasText(channel) ? List.of(channel) : List.of(),
                    "YouTube",
                    thumbnail(item),
                    0,
                    "https://www.youtube.com/watch?v=" + videoId,
                    PROVIDER,
                    "youtube",
                    videoId,
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
        return builder.baseUrl(trimTrailingSlash(properties.youtube().baseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    private static String thumbnail(JsonNode item) {
        for (String size : List.of("high", "medium", "default")) {
            String value = item.path("snippet").path("thumbnails").path(size).path("url").asText("");
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
