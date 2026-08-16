package com.example.agent.service.impl;

import com.example.agent.config.AgentProperties;
import com.example.agent.config.MusicPersonalizationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class MusicEmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(MusicEmbeddingClient.class);

    private final MusicPersonalizationProperties.Embedding properties;
    private final RestClient client;
    private final boolean configured;

    public MusicEmbeddingClient(MusicPersonalizationProperties personalization, AgentProperties agent) {
        this.properties = personalization.embedding();
        String apiKey = text(properties.apiKey(), agent.apiKey());
        String baseUrl = text(properties.baseUrl(), agent.baseUrl());
        configured = personalization.enabled() && properties.enabled()
                && apiKey != null && baseUrl != null;
        if (!configured) {
            client = null;
            return;
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        requests.setReadTimeout(Duration.ofSeconds(properties.timeoutSeconds()));
        client = RestClient.builder()
                .baseUrl(stripTrailingSlash(baseUrl))
                .requestFactory(requests)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public boolean configured() {
        return configured;
    }

    public String model() {
        return properties.model();
    }

    public int dimensions() {
        return properties.dimensions();
    }

    public List<List<Double>> embed(List<String> inputs) {
        if (!configured || inputs == null || inputs.isEmpty()) return List.of();
        List<List<Double>> output = new ArrayList<>();
        for (int offset = 0; offset < inputs.size(); offset += properties.batchSize()) {
            List<String> batch = inputs.subList(offset, Math.min(inputs.size(), offset + properties.batchSize()));
            EmbeddingResponse response = client.post().uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.model(),
                            "input", batch,
                            "dimensions", properties.dimensions()))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (response == null || response.data() == null || response.data().size() != batch.size()) {
                throw new IllegalStateException("Embedding service returned an incomplete batch");
            }
            response.data().stream().sorted(Comparator.comparingInt(EmbeddingDatum::index))
                    .map(EmbeddingDatum::embedding).forEach(output::add);
        }
        if (output.stream().anyMatch(vector -> vector == null || vector.size() != properties.dimensions())) {
            throw new IllegalStateException("Embedding service returned a vector with unexpected dimensions");
        }
        return List.copyOf(output);
    }

    public List<Double> embedOne(String input) {
        List<List<Double>> vectors = embed(List.of(input));
        return vectors.isEmpty() ? List.of() : vectors.get(0);
    }

    public static double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) return 0;
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        if (leftNorm == 0 || rightNorm == 0) return 0;
        return Math.max(0, Math.min(1, (dot / Math.sqrt(leftNorm * rightNorm) + 1) / 2));
    }

    private static String text(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred.strip();
        if (fallback != null && !fallback.isBlank()) return fallback.strip();
        return null;
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private record EmbeddingResponse(List<EmbeddingDatum> data) {
    }

    private record EmbeddingDatum(int index, List<Double> embedding) {
    }
}
