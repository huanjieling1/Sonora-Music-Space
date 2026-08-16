package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QqMusicCatalogProviderTest {
    @Test
    void mapsSearchResultsToLazyPlayableTracks() throws Exception {
        QqMusicSidecarClient sidecar = mock(QqMusicSidecarClient.class);
        QqMusicSessionStore sessions = mock(QqMusicSessionStore.class);
        when(sessions.hasSession()).thenReturn(true);
        when(sidecar.healthy()).thenReturn(true);
        when(sidecar.search("晴天 周杰伦", 5, 2)).thenReturn(new ObjectMapper().readTree("""
                {"tracks":[{
                  "songMid":"0039MnYb0qxYhV","mediaMid":"003Qui1q2u1Zho","name":"晴天",
                  "artists":["周杰伦"],"album":"叶惠美","albumMid":"000MkMni19ClKG",
                  "durationMs":269000
                }]}
                """));
        var provider = new QqMusicCatalogProvider(properties(), sidecar, sessions);

        var tracks = provider.search("晴天 周杰伦", 2, 5);

        assertThat(tracks).singleElement().satisfies(track -> {
            assertThat(track.id()).isEqualTo("qq:0039MnYb0qxYhV");
            assertThat(track.provider()).isEqualTo("qq");
            assertThat(track.playbackType()).isEqualTo("audio");
            assertThat(track.playbackUrl()).contains("/api/music/qq/play/0039MnYb0qxYhV")
                    .contains("mediaId=003Qui1q2u1Zho");
            assertThat(track.imageUrl()).contains("000MkMni19ClKG");
            assertThat(track.durationMs()).isEqualTo(269000);
        });
    }

    private static MusicCatalogProperties properties() {
        return new MusicCatalogProperties(5,
                new MusicCatalogProperties.Jamendo("", "https://jamendo.test"),
                new MusicCatalogProperties.Audius("", "https://audius.test"),
                new MusicCatalogProperties.Youtube("", "https://youtube.test"),
                new MusicCatalogProperties.Qq(true, "http://qq.test", "runtime-data", "flac"));
    }
}
