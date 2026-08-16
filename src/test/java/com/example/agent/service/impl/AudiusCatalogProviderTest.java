package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AudiusCatalogProviderTest {
    @Test
    void mapsPublicStreamableTracksAndSkipsGatedContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new AudiusCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tracks/search")))
                .andExpect(queryParam("query", "future%20bass"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer audius-key"))
                .andRespond(withSuccess("""
                        {"data":[{
                          "id":"abc","title":"Skyline","duration":201,"permalink":"artist/skyline",
                          "is_available":true,"is_stream_gated":false,"stream":true,
                          "artwork":{"480x480":"https://img.test/a.jpg"},"user":{"name":"Nova"}
                        },{
                          "id":"gated","title":"Private","is_stream_gated":true,"user":{"name":"Nova"}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        var tracks = provider.search("future bass", 4);

        assertThat(tracks).singleElement().satisfies(track -> {
            assertThat(track.id()).isEqualTo("audius:abc");
            assertThat(track.artists()).containsExactly("Nova");
            assertThat(track.playbackUrl()).isEqualTo("https://audius.test/v1/tracks/abc/stream");
            assertThat(track.externalUrl()).isEqualTo("https://audius.co/artist/skyline");
        });
        server.verify();
    }

    @Test
    void translatesPageNumberToAudiusOffset() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new AudiusCatalogProvider(properties(), builder.build());
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/tracks/search")))
                .andExpect(queryParam("limit", "10"))
                .andExpect(queryParam("offset", "190"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        provider.search("future bass", 20, 10);

        server.verify();
    }

    private static MusicCatalogProperties properties() {
        return new MusicCatalogProperties(5,
                new MusicCatalogProperties.Jamendo("", "https://jamendo.test/v3.0"),
                new MusicCatalogProperties.Audius("audius-key", "https://audius.test/v1"),
                new MusicCatalogProperties.Youtube("", "https://youtube.test/v3"));
    }
}
