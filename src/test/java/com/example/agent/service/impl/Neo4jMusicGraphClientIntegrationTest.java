package com.example.agent.service.impl;

import com.example.agent.config.MusicPersonalizationProperties;
import com.example.agent.model.bo.MusicTrackBo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "NEO4J_PASSWORD", matches = ".+")
class Neo4jMusicGraphClientIntegrationTest {
    private static final long USER_ID = Long.MAX_VALUE - 7;
    private static final String TRACK_KEY = "__sonora_graph_integration_track__";
    private static final String USER_KEY = Long.toString(USER_ID);
    private Neo4jMusicGraphClient graph;
    private MusicEmbeddingClient embeddings;
    private Driver cleanupDriver;

    @BeforeEach
    void setUp() {
        String uri = environment("NEO4J_URI", "bolt://127.0.0.1:7687");
        String username = environment("NEO4J_USERNAME", "neo4j");
        String password = System.getenv("NEO4J_PASSWORD");
        var properties = new MusicPersonalizationProperties(true, "baseline-v1",
                new MusicPersonalizationProperties.Embedding(false, "", "", "embedding-3", 512, 8, 64),
                new MusicPersonalizationProperties.Neo4j(true, uri, username, password,
                        environment("NEO4J_DATABASE", "neo4j")),
                new MusicPersonalizationProperties.Ranking(60, 0.65, 0.15, 0.7, 2, 0.08));
        embeddings = mock(MusicEmbeddingClient.class);
        when(embeddings.dimensions()).thenReturn(512);
        graph = new Neo4jMusicGraphClient(properties, embeddings, new ObjectMapper().findAndRegisterModules());
        graph.initialize();
        cleanupDriver = GraphDatabase.driver(uri, AuthTokens.basic(username, password));
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
        graph.close();
        cleanupDriver.close();
    }

    @Test
    void projectsSongBehaviorAndPreferenceThenRecallsByGraph() {
        MusicTrackBo track = new MusicTrackBo("qq:integration", "Integration Track",
                List.of("Integration Artist"), "Integration Album", "https://image", 120_000,
                "https://external", "qq", "audio", "https://audio", null);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        @SuppressWarnings("unchecked")
        Map<String, Object> trackMap = mapper.convertValue(track, Map.class);
        graph.project(new MusicPersonalizationRepository.OutboxRow(1, USER_ID, "UPSERT_SONG", Map.of(
                "trackKey", TRACK_KEY, "track", trackMap,
                "tags", List.of("__integration_tag__"), "contentText", "integration",
                "contentHash", "integration-hash"), 0));
        graph.project(new MusicPersonalizationRepository.OutboxRow(2, USER_ID, "BEHAVIOR_EVENT", Map.of(
                "userId", USER_ID, "trackKey", TRACK_KEY, "eventType", "LIKE", "reward", 2), 0));
        graph.project(new MusicPersonalizationRepository.OutboxRow(3, USER_ID, "UPSERT_PREFERENCE", Map.of(
                "userId", USER_ID, "id", "__integration_memory__", "type", "TAG",
                "value", "__integration_tag__", "polarity", 1, "confidence", 0.9, "layer", "L2"), 0));

        assertThat(graph.tagRecall(List.of("__integration_tag__"), 10))
                .extracting(Neo4jMusicGraphClient.GraphCandidate::trackKey).contains(TRACK_KEY);
        assertThat(graph.affinity(USER_ID, List.of(TRACK_KEY)).get(TRACK_KEY)).isPositive();
    }

    @Test
    void embeddingFailureDoesNotTakeTheGraphOffline() {
        when(embeddings.configured()).thenReturn(true);
        when(embeddings.embedOne("integration")).thenThrow(new IllegalStateException("embedding unavailable"));
        MusicTrackBo track = new MusicTrackBo("qq:integration", "Integration Track",
                List.of("Integration Artist"), "Integration Album", "https://image", 120_000,
                "https://external", "qq", "audio", "https://audio", null);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        @SuppressWarnings("unchecked")
        Map<String, Object> trackMap = mapper.convertValue(track, Map.class);

        assertThatThrownBy(() -> graph.project(new MusicPersonalizationRepository.OutboxRow(
                4, USER_ID, "UPSERT_SONG", Map.of(
                "trackKey", TRACK_KEY, "track", trackMap,
                "tags", List.of("__integration_tag__"), "contentText", "integration",
                "contentHash", "integration-hash"), 0)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Embedding projection");

        assertThat(graph.ready()).isTrue();
        assertThat(graph.tagRecall(List.of("__integration_tag__"), 10))
                .extracting(Neo4jMusicGraphClient.GraphCandidate::trackKey).contains(TRACK_KEY);
    }

    private void cleanup() {
        if (cleanupDriver == null) return;
        cleanupDriver.executableQuery("""
                MATCH (n)
                WHERE (n:Song AND n.trackKey = $trackKey)
                   OR (n:User AND n.id = $userId)
                   OR (n:Artist AND n.name = 'Integration Artist')
                   OR (n:Tag AND n.key = 'integrationtag')
                DETACH DELETE n
                """).withParameters(Map.of("trackKey", TRACK_KEY, "userId", USER_KEY)).execute();
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
