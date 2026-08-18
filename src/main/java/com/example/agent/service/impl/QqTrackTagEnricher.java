package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicTrackBo;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class QqTrackTagEnricher {
    private static final String SOURCE = "qq_album";
    private static final int REFRESH_DAYS = 30;

    private final QqMusicSidecarClient sidecar;
    private final MusicPersonalizationRepository repository;

    public QqTrackTagEnricher(QqMusicSidecarClient sidecar, MusicPersonalizationRepository repository) {
        this.sidecar = sidecar;
        this.repository = repository;
    }

    public void enrich(String trackKey, MusicTrackBo track) {
        if (track == null || !"qq".equalsIgnoreCase(track.provider())
                || !StringUtils.hasText(track.albumId()) || !sidecar.enabled()
                || !repository.shouldEnrichTrack(trackKey, SOURCE, REFRESH_DAYS)) {
            return;
        }
        try {
            JsonNode album = sidecar.album(track.albumId());
            List<MusicPersonalizationRepository.TrackTagRow> tags = new ArrayList<>();
            add(tags, "GENRE", album == null ? null : album.path("genre").asText(null), 0.95);
            add(tags, "LANGUAGE", album == null ? null : album.path("language").asText(null), 0.95);
            repository.saveTrackEnrichment(trackKey, SOURCE, track.albumId(),
                    tags.isEmpty() ? "EMPTY" : "SUCCESS", null, tags);
        } catch (RuntimeException exception) {
            repository.saveTrackEnrichment(trackKey, SOURCE, track.albumId(), "FAILED",
                    exception.getMessage(), List.of());
        }
    }

    private static void add(List<MusicPersonalizationRepository.TrackTagRow> tags,
                            String type, String value, double confidence) {
        if (StringUtils.hasText(value)) {
            tags.add(new MusicPersonalizationRepository.TrackTagRow(type, value.strip(), confidence));
        }
    }
}
