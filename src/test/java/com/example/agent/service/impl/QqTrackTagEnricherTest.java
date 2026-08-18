package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicTrackBo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QqTrackTagEnricherTest {
    @Test
    void storesAlbumGenreAndLanguageWithAuditableSource() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        MusicPersonalizationRepository repository = mock(MusicPersonalizationRepository.class);
        when(sidecar.enabled()).thenReturn(true);
        when(repository.shouldEnrichTrack("track-key", "qq_album", 30)).thenReturn(true);
        when(sidecar.album("album-mid")).thenReturn(new ObjectMapper().readTree(
                "{\"genre\":\"流行\",\"language\":\"国语\"}"));
        MusicTrackBo track = new MusicTrackBo("qq:song", "歌", List.of("歌手"), "专辑", null,
                180_000, null, "qq", "audio", "/play", null).withAlbumId("album-mid");

        new QqTrackTagEnricher(sidecar, repository).enrich("track-key", track);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MusicPersonalizationRepository.TrackTagRow>> tags = ArgumentCaptor.forClass(List.class);
        verify(repository).saveTrackEnrichment(anyString(), anyString(), anyString(), anyString(), any(), tags.capture());
        assertThat(tags.getValue()).extracting(item -> item.type() + ":" + item.value())
                .containsExactly("GENRE:流行", "LANGUAGE:国语");
    }

    @Test
    void skipsTracksThatHaveNoQqAlbumIdentity() {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        MusicPersonalizationRepository repository = mock(MusicPersonalizationRepository.class);
        MusicTrackBo track = new MusicTrackBo("audius:song", "歌", List.of("歌手"), "专辑", null,
                180_000, null, "audius", "audio", "/play", null);

        new QqTrackTagEnricher(sidecar, repository).enrich("track-key", track);

        verify(repository, never()).saveTrackEnrichment(anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(repository, never()).shouldEnrichTrack(anyString(), anyString(), anyInt());
    }
}
