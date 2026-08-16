package com.example.agent.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MusicGraphProjectionService {
    private static final Logger log = LoggerFactory.getLogger(MusicGraphProjectionService.class);

    private final MusicPersonalizationRepository repository;
    private final Neo4jMusicGraphClient graph;
    private final MusicEmbeddingClient embeddings;

    public MusicGraphProjectionService(MusicPersonalizationRepository repository,
                                       Neo4jMusicGraphClient graph,
                                       MusicEmbeddingClient embeddings) {
        this.repository = repository;
        this.graph = graph;
        this.embeddings = embeddings;
    }

    @Scheduled(fixedDelayString = "${music.personalization.outbox-delay-ms:5000}")
    public void projectPending() {
        if (!graph.ready()) return;
        for (var row : repository.pendingOutbox(50)) {
            try {
                graph.project(row);
                repository.markOutboxProcessed(row.id());
                if ("UPSERT_SONG".equals(row.eventType())) {
                    String trackKey = String.valueOf(row.payload().get("trackKey"));
                    String contentHash = String.valueOf(row.payload().get("contentHash"));
                    repository.markGraphProjected(trackKey, contentHash, embeddings.model(),
                            embeddings.dimensions(), embeddings.configured());
                }
            } catch (RuntimeException exception) {
                int attempts = row.attempts() + 1;
                repository.markOutboxRetry(row.id(), attempts, exception.getClass().getSimpleName());
                log.warn("Music graph outbox {} failed on attempt {}", row.id(), attempts);
            }
        }
    }
}
