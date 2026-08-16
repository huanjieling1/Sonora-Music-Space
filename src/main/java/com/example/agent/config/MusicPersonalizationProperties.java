package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "music.personalization")
public record MusicPersonalizationProperties(
        boolean enabled,
        String activePolicyVersion,
        Embedding embedding,
        Neo4j neo4j,
        Ranking ranking
) {
    public MusicPersonalizationProperties {
        activePolicyVersion = text(activePolicyVersion, "baseline-v1");
        embedding = embedding == null ? new Embedding(false, "", "", "embedding-3", 512, 8, 64) : embedding;
        neo4j = neo4j == null ? new Neo4j(false, "", "", "", "neo4j") : neo4j;
        ranking = ranking == null ? new Ranking(60, 0.65, 0.15, 0.7, 2, 0.08) : ranking;
    }

    public record Embedding(boolean enabled, String apiKey, String baseUrl, String model,
                            int dimensions, int timeoutSeconds, int batchSize) {
        public Embedding {
            model = text(model, "embedding-3");
            dimensions = dimensions <= 0 ? 512 : Math.min(2048, Math.max(256, dimensions));
            timeoutSeconds = timeoutSeconds <= 0 ? 8 : Math.min(60, timeoutSeconds);
            batchSize = batchSize <= 0 ? 64 : Math.min(64, batchSize);
        }
    }

    public record Neo4j(boolean enabled, String uri, String username, String password, String database) {
        public Neo4j {
            uri = text(uri, "bolt://127.0.0.1:7687");
            username = text(username, "neo4j");
            database = text(database, "neo4j");
        }
    }

    public record Ranking(int rrfK, double coarseCutRatio, double explorationRatio,
                          double mmrLambda, int maxTracksPerArtist, double personalizationDeltaLimit) {
        public Ranking {
            rrfK = rrfK <= 0 ? 60 : rrfK;
            coarseCutRatio = bounded(coarseCutRatio, 0.65, 0.1, 1.0);
            explorationRatio = bounded(explorationRatio, 0.15, 0.0, 0.5);
            mmrLambda = bounded(mmrLambda, 0.7, 0.0, 1.0);
            maxTracksPerArtist = maxTracksPerArtist <= 0 ? 2 : maxTracksPerArtist;
            personalizationDeltaLimit = bounded(personalizationDeltaLimit, 0.08, 0.0, 0.2);
        }
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static double bounded(double value, double fallback, double low, double high) {
        double actual = Double.isFinite(value) && value != 0 ? value : fallback;
        return Math.max(low, Math.min(high, actual));
    }
}
