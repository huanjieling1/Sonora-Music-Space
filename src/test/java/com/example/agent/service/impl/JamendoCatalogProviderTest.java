package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JamendoCatalogProviderTest {
    @Test
    void usesDedicatedTrackAndArtistParametersForExactSearch() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new JamendoCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tracks/")))
                .andExpect(queryParam("namesearch", "Faded"))
                .andExpect(queryParam("artist_name", "Alan%20Walker"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        provider.search(new MusicSearchTask(MusicSearchTaskType.TRACK_ARTIST,
                "Faded Alan Walker", "Faded", "Alan Walker", null), 5);

        server.verify();
    }

    @Test
    void mapsPlayableTracksAndLicenseMetadata() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new JamendoCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tracks/")))
                .andExpect(queryParam("client_id", "jamendo-key"))
                .andExpect(queryParam("search", "deep%20focus"))
                .andExpect(queryParam("limit", "2"))
                .andRespond(withSuccess("""
                        {"results":[{
                          "id":"42","name":"Soft Focus","artist_name":"Ada","album_name":"Night",
                          "image":"https://img.test/cover.jpg","duration":185,
                          "audio":"https://audio.test/track.mp3","shareurl":"https://jamendo.test/track/42",
                          "license_ccurl":"https://creativecommons.org/licenses/by/4.0/"
                        }]}
                        """, MediaType.APPLICATION_JSON));

        var tracks = provider.search("deep focus", 2);

        assertThat(tracks).singleElement().satisfies(track -> {
            assertThat(track.id()).isEqualTo("jamendo:42");
            assertThat(track.playbackType()).isEqualTo("audio");
            assertThat(track.playbackUrl()).isEqualTo("https://audio.test/track.mp3");
            assertThat(track.durationMs()).isEqualTo(185000);
            assertThat(track.licenseUrl()).contains("creativecommons.org");
        });
        server.verify();
    }

    @Test
    void ignoresTracksWithoutSecurePlayableAudio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new JamendoCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tracks/")))
                .andRespond(withSuccess("""
                        {"results":[{"id":"1","name":"Missing","audio":""},
                        {"id":"2","name":"Unsafe","audio":"http://audio.test/file.mp3"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(provider.search("focus", 2)).isEmpty();
        server.verify();
    }

    @Test
    void translatesPageNumberToJamendoOffset() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new JamendoCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tracks/")))
                .andExpect(queryParam("limit", "10"))
                .andExpect(queryParam("offset", "30"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        provider.search(new MusicSearchTask(MusicSearchTaskType.KEYWORDS,
                "focus", null, null, null), 4, 10);

        server.verify();
    }

    private static MusicCatalogProperties properties() {
        return new MusicCatalogProperties(5,
                new MusicCatalogProperties.Jamendo("jamendo-key", "https://jamendo.test/v3.0"),
                new MusicCatalogProperties.Audius("", "https://audius.test/v1"),
                new MusicCatalogProperties.Youtube("", "https://youtube.test/v3"));
    }
}
