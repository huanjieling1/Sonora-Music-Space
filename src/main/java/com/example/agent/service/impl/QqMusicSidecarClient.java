package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class QqMusicSidecarClient {
    private static final String COOKIE_HEADER = "X-QQ-Music-Cookie";

    private final MusicCatalogProperties.Qq configuration;
    private final RestClient restClient;

    @Autowired
    public QqMusicSidecarClient(MusicCatalogProperties properties, RestClient.Builder builder) {
        this.configuration = properties.qq();
        var requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.resolvedTimeoutSeconds());
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder.baseUrl(trimTrailingSlash(configuration.baseUrl()))
                .requestFactory(requestFactory)
                .build();
    }

    QqMusicSidecarClient(MusicCatalogProperties.Qq configuration, RestClient restClient) {
        this.configuration = configuration;
        this.restClient = restClient;
    }

    public boolean enabled() {
        return configuration != null && configuration.configured();
    }

    public boolean healthy() {
        if (!enabled()) {
            return false;
        }
        try {
            JsonNode response = restClient.get().uri("/health").retrieve().body(JsonNode.class);
            return response != null && response.path("ready").asBoolean(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public JsonNode search(String query, int limit) {
        return search(query, limit, 1);
    }

    public JsonNode search(String query, int limit, int page) {
        return search(query, "TRACK", limit, page);
    }

    public JsonNode search(String query, String type, int limit, int page) {
        return restClient.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("key", query)
                        .queryParam("type", type)
                        .queryParam("limit", limit)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode play(String songMid, String mediaId, String quality, String cookie) {
        return restClient.get()
                .uri(uri -> {
                    var builder = uri.path("/play")
                            .queryParam("songmid", songMid)
                            .queryParam("quality", quality);
                    if (StringUtils.hasText(mediaId)) {
                        builder.queryParam("mediaId", mediaId);
                    }
                    return builder.build();
                })
                .header(COOKIE_HEADER, cookie)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode lyrics(String songMid) {
        return restClient.get()
                .uri(uri -> uri.path("/lyric")
                        .queryParam("songmid", songMid)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode publicPlaylists(int page, int limit) {
        return restClient.get()
                .uri(uri -> uri.path("/home/playlists")
                        .queryParam("page", page)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode playlist(String playlistId, int limit) {
        return restClient.get()
                .uri(uri -> uri.path("/playlist")
                        .queryParam("id", playlistId)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode artist(String artistMid, int songPage, int songLimit, int albumPage, int albumLimit) {
        return restClient.get()
                .uri(uri -> uri.path("/artist")
                        .queryParam("mid", artistMid)
                        .queryParam("songPage", songPage)
                        .queryParam("songLimit", songLimit)
                        .queryParam("albumPage", albumPage)
                        .queryParam("albumLimit", albumLimit)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode album(String albumMid) {
        return restClient.get().uri(uri -> uri.path("/album").queryParam("mid", albumMid).build())
                .retrieve().body(JsonNode.class);
    }

    public JsonNode video(String videoId) {
        return restClient.get().uri(uri -> uri.path("/video").queryParam("id", videoId).build())
                .retrieve().body(JsonNode.class);
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
