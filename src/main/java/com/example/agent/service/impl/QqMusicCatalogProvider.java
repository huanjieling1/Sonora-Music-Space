package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.MusicCatalogProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class QqMusicCatalogProvider implements MusicCatalogProvider {
    private static final String PROVIDER = "qq";

    private final MusicCatalogProperties.Qq configuration;
    private final QqMusicSidecarClient sidecar;
    private final QqMusicSessionStore sessionStore;

    public QqMusicCatalogProvider(MusicCatalogProperties properties,
                                  QqMusicSidecarClient sidecar,
                                  QqMusicSessionStore sessionStore) {
        this.configuration = properties.qq();
        this.sidecar = sidecar;
        this.sessionStore = sessionStore;
    }

    @Override
    public String id() {
        return PROVIDER;
    }

    @Override
    public String displayName() {
        return "QQ 音乐";
    }

    @Override
    public boolean configured() {
        return configuration != null && configuration.configured() && sessionStore.hasSession() && sidecar.healthy();
    }

    @Override
    public boolean fallbackOnly() {
        return false;
    }

    @Override
    public int order() {
        return 1;
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
        if (!configured() || !StringUtils.hasText(query) || pageSize <= 0) {
            return List.of();
        }
        JsonNode response = sidecar.search(query, pageSize, page);
        if (response == null || !response.path("tracks").isArray()) {
            return List.of();
        }
        List<MusicTrackBo> tracks = new ArrayList<>();
        for (JsonNode item : response.path("tracks")) {
            String songMid = item.path("songMid").asText("");
            String mediaMid = item.path("mediaMid").asText(songMid);
            String name = item.path("name").asText("");
            if (!songMid.matches("[A-Za-z0-9]+") || !StringUtils.hasText(name)) {
                continue;
            }
            List<String> artists = new ArrayList<>();
            if (item.path("artists").isArray()) {
                item.path("artists").forEach(artist -> {
                    if (StringUtils.hasText(artist.asText())) {
                        artists.add(artist.asText());
                    }
                });
            }
            String albumMid = item.path("albumMid").asText("");
            String imageUrl = albumMid.matches("[A-Za-z0-9]+")
                    ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg?max_age=2592000"
                    : null;
            String playbackUrl = UriComponentsBuilder.fromPath("/api/music/qq/play/")
                    .pathSegment(songMid)
                    .queryParam("mediaId", mediaMid)
                    .build().encode().toUriString();
            tracks.add(new MusicTrackBo(
                    PROVIDER + ":" + songMid,
                    name,
                    List.copyOf(artists),
                    item.path("album").asText(""),
                    imageUrl,
                    Math.max(0, item.path("durationMs").asLong()),
                    "https://y.qq.com/n/ryqq/songDetail/" + songMid,
                    PROVIDER,
                    "audio",
                    playbackUrl,
                    null
            ));
        }
        return tracks;
    }

    @Override
    public List<MusicTrackBo> search(MusicSearchTask task, int limit) {
        return search(task.query(), limit);
    }

    @Override
    public List<MusicTrackBo> search(MusicSearchTask task, int page, int pageSize) {
        return search(task.query(), page, pageSize);
    }
}
