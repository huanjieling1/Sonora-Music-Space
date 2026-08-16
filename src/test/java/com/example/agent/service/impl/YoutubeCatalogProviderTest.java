package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class YoutubeCatalogProviderTest {
    @Test
    void mapsEmbeddableVideoAsYoutubePlayback() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new YoutubeCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andExpect(queryParam("key", "youtube-key"))
                .andExpect(queryParam("videoEmbeddable", "true"))
                .andExpect(queryParam("videoSyndicated", "true"))
                .andRespond(withSuccess("""
                        {"items":[{
                          "id":{"videoId":"video-1"},
                          "snippet":{"title":"Focus &amp; Flow","channelTitle":"Official Artist",
                            "thumbnails":{"high":{"url":"https://img.test/y.jpg"}}}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        var tracks = provider.search("focus music", 1);

        assertThat(tracks).singleElement().satisfies(track -> {
            assertThat(track.id()).isEqualTo("youtube:video-1");
            assertThat(track.name()).isEqualTo("Focus & Flow");
            assertThat(track.playbackType()).isEqualTo("youtube");
            assertThat(track.playbackUrl()).isEqualTo("video-1");
            assertThat(track.externalUrl()).endsWith("video-1");
        });
        server.verify();
    }

    @Test
    void usesOfficialNextPageTokenForSequentialLoading() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new YoutubeCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andExpect(queryParam("maxResults", "10"))
                .andRespond(withSuccess("{\"nextPageToken\":\"next-token\",\"items\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/search")))
                .andExpect(queryParam("pageToken", "next-token"))
                .andRespond(withSuccess("{\"items\":[]}", MediaType.APPLICATION_JSON));

        provider.search("focus music", 1, 10);
        provider.search("focus music", 2, 10);

        server.verify();
    }

    private static MusicCatalogProperties properties() {
        return new MusicCatalogProperties(5,
                new MusicCatalogProperties.Jamendo("", "https://jamendo.test/v3.0"),
                new MusicCatalogProperties.Audius("", "https://audius.test/v1"),
                new MusicCatalogProperties.Youtube("youtube-key", "https://youtube.test/v3"));
    }
}
