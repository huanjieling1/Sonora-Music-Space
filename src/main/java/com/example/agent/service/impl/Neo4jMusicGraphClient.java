package com.example.agent.service.impl;

import com.example.agent.config.MusicPersonalizationProperties;
import com.example.agent.model.bo.MusicTrackBo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.QueryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class Neo4jMusicGraphClient {
    private static final Logger log = LoggerFactory.getLogger(Neo4jMusicGraphClient.class);

    private final MusicPersonalizationProperties properties;
    private final MusicEmbeddingClient embeddings;
    private final ObjectMapper objectMapper;
    private Driver driver;
    private volatile boolean ready;
    private volatile boolean vectorReady;
    private volatile long lastHealthCheckNanos;

    public Neo4jMusicGraphClient(MusicPersonalizationProperties properties,
                                 MusicEmbeddingClient embeddings,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.embeddings = embeddings;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    synchronized void initialize() {
        var graph = properties.neo4j();
        if (!properties.enabled() || !graph.enabled() || graph.password() == null || graph.password().isBlank()) {
            log.info("Music graph personalization is disabled or has no Neo4j password; catalog fallback remains active");
            return;
        }
        if (ready && driver != null) return;
        try {
            closeDriver();
            driver = GraphDatabase.driver(graph.uri(), AuthTokens.basic(graph.username(), graph.password()));
            driver.verifyConnectivity();
            execute("CREATE CONSTRAINT music_user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE", Map.of());
            execute("CREATE CONSTRAINT music_song_key IF NOT EXISTS FOR (s:Song) REQUIRE s.trackKey IS UNIQUE", Map.of());
            execute("CREATE CONSTRAINT music_artist_name IF NOT EXISTS FOR (a:Artist) REQUIRE a.name IS UNIQUE", Map.of());
            execute("CREATE CONSTRAINT music_tag_key IF NOT EXISTS FOR (t:Tag) REQUIRE t.key IS UNIQUE", Map.of());
            execute("""
                    CREATE VECTOR INDEX music_song_embedding IF NOT EXISTS
                    FOR (s:Song) ON (s.embedding)
                    OPTIONS {indexConfig: {
                        `vector.dimensions`: %d,
                        `vector.similarity_function`: 'cosine'
                    }}
                    """.formatted(embeddings.dimensions()), Map.of());
            ready = true;
            lastHealthCheckNanos = System.nanoTime();
            refreshVectorReady();
            log.info("Music Neo4j graph is ready at {}", graph.uri());
        } catch (RuntimeException exception) {
            ready = false;
            closeDriver();
            log.warn("Music Neo4j is unavailable; recommendations will degrade safely: {}",
                    exception.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void close() {
        closeDriver();
    }

    public boolean ready() {
        if (ready && driver != null
                && System.nanoTime() - lastHealthCheckNanos > java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) {
            synchronized (this) {
                if (ready && driver != null
                        && System.nanoTime() - lastHealthCheckNanos
                        > java.util.concurrent.TimeUnit.SECONDS.toNanos(5)) {
                    try {
                        driver.verifyConnectivity();
                        lastHealthCheckNanos = System.nanoTime();
                    } catch (RuntimeException exception) {
                        degrade(exception);
                    }
                }
            }
        }
        return ready;
    }

    @Scheduled(fixedDelayString = "${music.personalization.neo4j-reconnect-delay-ms:30000}")
    void reconnect() {
        var graph = properties.neo4j();
        if (properties.enabled() && graph.enabled() && graph.password() != null
                && !graph.password().isBlank() && !ready) initialize();
    }

    public boolean vectorReady() {
        if (ready && !vectorReady) refreshVectorReady();
        return vectorReady;
    }

    public List<GraphCandidate> vectorRecall(List<Double> queryVector, int limit) {
        if (!vectorReady() || queryVector == null || queryVector.isEmpty() || limit <= 0) return List.of();
        try {
            return execute("""
                    CALL db.index.vector.queryNodes('music_song_embedding', $limit, $vector)
                    YIELD node, score
                    WHERE node.snapshotJson IS NOT NULL
                    RETURN node.trackKey AS trackKey, node.snapshotJson AS snapshotJson,
                           node.tags AS tags, score
                    ORDER BY score DESC
                    """, Map.of("limit", limit, "vector", queryVector)).records().stream()
                    .map(record -> new GraphCandidate(record.get("trackKey").asString(),
                            readTrack(record.get("snapshotJson").asString()),
                            record.get("tags").isNull() ? List.of() : record.get("tags").asList(value -> value.asString()),
                            record.get("score").asDouble()))
                    .toList();
        } catch (RuntimeException exception) {
            degrade(exception);
            return List.of();
        }
    }

    public List<GraphCandidate> tagRecall(List<String> tags, int limit) {
        if (!ready || tags == null || tags.isEmpty() || limit <= 0) return List.of();
        List<String> normalized = tags.stream().map(MusicTextNormalizer::normalize)
                .filter(value -> !value.isBlank()).distinct().toList();
        if (normalized.isEmpty()) return List.of();
        try {
            return execute("""
                    UNWIND $tags AS wanted
                    MATCH (t:Tag {key: wanted})<-[:HAS_TAG]-(s:Song)
                    WHERE s.snapshotJson IS NOT NULL
                    WITH s, count(DISTINCT t) AS matches
                    RETURN s.trackKey AS trackKey, s.snapshotJson AS snapshotJson,
                           s.tags AS tags, toFloat(matches) / size($tags) AS score
                    ORDER BY score DESC, s.lastSeenAt DESC LIMIT $limit
                    """, Map.of("tags", normalized, "limit", limit)).records().stream()
                    .map(record -> new GraphCandidate(record.get("trackKey").asString(),
                            readTrack(record.get("snapshotJson").asString()),
                            record.get("tags").isNull() ? List.of() : record.get("tags").asList(value -> value.asString()),
                            record.get("score").asDouble()))
                    .toList();
        } catch (RuntimeException exception) {
            degrade(exception);
            return List.of();
        }
    }

    public Map<String, Double> affinity(long userId, List<String> trackKeys) {
        if (!ready || trackKeys == null || trackKeys.isEmpty()) return Map.of();
        try {
            Map<String, Double> result = new LinkedHashMap<>();
            execute("""
                    MATCH (s:Song) WHERE s.trackKey IN $trackKeys
                    OPTIONAL MATCH (u:User {id: $userId})-[d]->(s)
                    WITH s, sum(CASE type(d)
                        WHEN 'LIKES' THEN 1.0 WHEN 'SAVED' THEN 1.0
                        WHEN 'DISLIKES' THEN -1.0 WHEN 'LISTENED' THEN 0.15 ELSE 0.0 END) AS direct
                    OPTIONAL MATCH (u:User {id: $userId})-[p:PREFERS|AVOIDS]->(t:Tag)<-[:HAS_TAG]-(s)
                    WITH s, direct, sum(CASE type(p)
                        WHEN 'PREFERS' THEN coalesce(p.confidence, 0.5)
                        WHEN 'AVOIDS' THEN -coalesce(p.confidence, 0.5) ELSE 0.0 END) AS tagAffinity
                    RETURN s.trackKey AS trackKey, direct + tagAffinity AS affinity
                    """, Map.of("trackKeys", trackKeys, "userId", Long.toString(userId))).records()
                    .forEach(record -> result.put(record.get("trackKey").asString(),
                            Math.max(-1, Math.min(1, record.get("affinity").asDouble(0)))));
            return Map.copyOf(result);
        } catch (RuntimeException exception) {
            degrade(exception);
            return Map.of();
        }
    }

    public Map<String, CachedEmbedding> embeddingVectors(List<String> trackKeys) {
        if (!ready() || trackKeys == null || trackKeys.isEmpty()) return Map.of();
        try {
            Map<String, CachedEmbedding> result = new LinkedHashMap<>();
            execute("""
                    MATCH (s:Song)
                    WHERE s.trackKey IN $trackKeys AND s.embedding IS NOT NULL
                    RETURN s.trackKey AS trackKey, s.contentHash AS contentHash,
                           s.embedding AS embedding
                    """, Map.of("trackKeys", trackKeys)).records().forEach(record ->
                    result.put(record.get("trackKey").asString(), new CachedEmbedding(
                            record.get("contentHash").asString(""),
                            record.get("embedding").asList(value -> value.asDouble()))));
            return Map.copyOf(result);
        } catch (RuntimeException exception) {
            degrade(exception);
            return Map.of();
        }
    }

    public void project(MusicPersonalizationRepository.OutboxRow row) {
        if (!ready) throw new IllegalStateException("Neo4j is unavailable");
        try {
            switch (row.eventType()) {
                case "UPSERT_SONG" -> upsertSong(row.payload());
                case "BEHAVIOR_EVENT" -> projectBehavior(row.payload());
                case "UPSERT_PREFERENCE" -> upsertPreference(row.payload());
                case "DELETE_PREFERENCE" -> deletePreference(row.payload());
                case "CLEAR_LEARNED" -> clearLearned(row.payload());
                default -> log.warn("Ignoring unsupported music graph event {}", row.eventType());
            }
        } catch (RuntimeException exception) {
            if (exception instanceof EmbeddingProjectionException) {
                throw exception;
            }
            degrade(exception);
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private void upsertSong(Map<String, Object> payload) {
        String trackKey = String.valueOf(payload.get("trackKey"));
        Map<String, Object> track = (Map<String, Object>) payload.getOrDefault("track", Map.of());
        List<String> tags = ((List<Object>) payload.getOrDefault("tags", List.of())).stream()
                .map(String::valueOf).filter(value -> !value.isBlank()).distinct().toList();
        String artist = firstArtist(track);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("trackKey", trackKey);
        params.put("title", String.valueOf(track.getOrDefault("name", "")));
        params.put("provider", String.valueOf(track.getOrDefault("provider", "")));
        params.put("providerTrackId", String.valueOf(track.getOrDefault("id", "")));
        params.put("snapshotJson", json(track));
        params.put("artist", artist);
        params.put("tags", tags);
        params.put("tagKeys", tags.stream().map(MusicTextNormalizer::normalize).toList());
        execute("""
                MERGE (s:Song {trackKey: $trackKey})
                SET s.title = $title, s.provider = $provider, s.providerTrackId = $providerTrackId,
                    s.snapshotJson = $snapshotJson, s.tags = $tags, s.lastSeenAt = datetime()
                FOREACH (_ IN CASE WHEN $artist = '' THEN [] ELSE [1] END |
                    MERGE (a:Artist {name: $artist}) MERGE (s)-[:PERFORMED_BY]->(a))
                WITH s
                UNWIND CASE WHEN size($tagKeys) = 0 THEN []
                            ELSE range(0, size($tagKeys) - 1) END AS index
                WITH s, $tagKeys[index] AS tagKey, $tags[index] AS tagValue
                WHERE tagKey <> ''
                MERGE (t:Tag {key: tagKey}) SET t.value = tagValue
                MERGE (s)-[:HAS_TAG]->(t)
                """, params);
        if (embeddings.configured()) {
            List<Double> vector;
            try {
                vector = embeddings.embedOne(String.valueOf(payload.getOrDefault("contentText", "")));
            } catch (RuntimeException exception) {
                throw new EmbeddingProjectionException(exception);
            }
            if (!vector.isEmpty()) {
                execute("""
                        MATCH (s:Song {trackKey: $trackKey})
                        CALL db.create.setNodeVectorProperty(s, 'embedding', $embedding)
                        SET s.embeddingModel = $model, s.embeddingDimensions = $dimensions,
                            s.contentHash = $contentHash
                        """, Map.of("trackKey", trackKey, "embedding", vector,
                        "model", embeddings.model(), "dimensions", embeddings.dimensions(),
                        "contentHash", String.valueOf(payload.getOrDefault("contentHash", ""))));
            }
        }
    }

    private void projectBehavior(Map<String, Object> payload) {
        String event = String.valueOf(payload.get("eventType"));
        Map<String, Object> keys = Map.of("userId", String.valueOf(payload.get("userId")),
                "trackKey", String.valueOf(payload.get("trackKey")));
        if ("UNSAVE".equals(event)) {
            execute("""
                    MATCH (:User {id: $userId})-[r:SAVED]->(:Song {trackKey: $trackKey}) DELETE r
                    """, keys);
            return;
        }
        if ("LIKE".equals(event) || "DISLIKE".equals(event)) {
            String opposing = "LIKE".equals(event) ? "DISLIKES" : "LIKES";
            execute("""
                    MATCH (:User {id: $userId})-[r:%s]->(:Song {trackKey: $trackKey}) DELETE r
                    """.formatted(opposing), keys);
        }
        String relation = switch (event) {
            case "LIKE" -> "LIKES";
            case "DISLIKE" -> "DISLIKES";
            case "SAVE" -> "SAVED";
            default -> "LISTENED";
        };
        String cypher = """
                MERGE (u:User {id: $userId})
                WITH u
                MATCH (s:Song {trackKey: $trackKey})
                MERGE (u)-[r:%s]->(s)
                SET r.count = coalesce(r.count, 0) + 1, r.lastAt = datetime(), r.lastEvent = $event
                """.formatted(relation);
        execute(cypher, Map.of("userId", String.valueOf(payload.get("userId")),
                "trackKey", String.valueOf(payload.get("trackKey")), "event", event));
    }

    private void upsertPreference(Map<String, Object> payload) {
        int polarity = ((Number) payload.getOrDefault("polarity", 1)).intValue();
        String relation = polarity < 0 ? "AVOIDS" : "PREFERS";
        String key = MusicTextNormalizer.normalize(String.valueOf(payload.get("value")));
        String cypher = """
                MERGE (u:User {id: $userId})
                MERGE (t:Tag {key: $key}) SET t.value = $value, t.type = $type
                MERGE (u)-[r:%s {memoryId: $memoryId}]->(t)
                SET r.confidence = $confidence, r.layer = $layer, r.updatedAt = datetime()
                """.formatted(relation);
        execute(cypher, Map.of("userId", String.valueOf(payload.get("userId")), "key", key,
                "value", String.valueOf(payload.get("value")), "type", String.valueOf(payload.get("type")),
                "memoryId", String.valueOf(payload.get("id")),
                "confidence", ((Number) payload.getOrDefault("confidence", 1)).doubleValue(),
                "layer", String.valueOf(payload.get("layer"))));
    }

    private void deletePreference(Map<String, Object> payload) {
        execute("""
                MATCH (:User {id: $userId})-[r:PREFERS|AVOIDS {memoryId: $memoryId}]->() DELETE r
                """, Map.of("userId", String.valueOf(payload.get("userId")),
                "memoryId", String.valueOf(payload.get("id"))));
    }

    private void clearLearned(Map<String, Object> payload) {
        execute("""
                MATCH (:User {id: $userId})-[r:PREFERS|AVOIDS]->()
                WHERE r.layer IN ['L2', 'L3'] DELETE r
                """, Map.of("userId", String.valueOf(payload.get("userId"))));
    }

    private org.neo4j.driver.EagerResult execute(String cypher, Map<String, Object> parameters) {
        return driver.executableQuery(cypher).withParameters(parameters)
                .withConfig(QueryConfig.builder().withDatabase(properties.neo4j().database()).build())
                .execute();
    }

    private synchronized void degrade(RuntimeException exception) {
        log.warn("Music graph query failed; current request will degrade: {}", exception.getClass().getSimpleName());
        ready = false;
        vectorReady = false;
        closeDriver();
    }

    private void refreshVectorReady() {
        try {
            vectorReady = execute("""
                    SHOW VECTOR INDEXES YIELD name, state
                    WHERE name = 'music_song_embedding'
                    RETURN state
                    """, Map.of()).records().stream()
                    .anyMatch(record -> "ONLINE".equals(record.get("state").asString("")));
        } catch (RuntimeException exception) {
            vectorReady = false;
        }
    }

    private MusicTrackBo readTrack(String json) {
        try {
            return objectMapper.readValue(json, MusicTrackBo.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid Neo4j music snapshot", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Music graph payload is not serializable", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static String firstArtist(Map<String, Object> track) {
        Object raw = track.get("artists");
        if (!(raw instanceof List<?> values) || values.isEmpty()) return "";
        return String.valueOf(values.get(0));
    }

    private void closeDriver() {
        vectorReady = false;
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    public record GraphCandidate(String trackKey, MusicTrackBo track, List<String> tags, double score) {
    }

    public record CachedEmbedding(String contentHash, List<Double> vector) {
        public CachedEmbedding {
            vector = vector == null ? List.of() : List.copyOf(vector);
        }
    }

    private static final class EmbeddingProjectionException extends RuntimeException {
        private EmbeddingProjectionException(RuntimeException cause) {
            super("Embedding projection is temporarily unavailable", cause);
        }
    }
}
