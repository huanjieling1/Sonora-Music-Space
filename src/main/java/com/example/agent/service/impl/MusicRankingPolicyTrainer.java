package com.example.agent.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MusicRankingPolicyTrainer {
    private static final Logger log = LoggerFactory.getLogger(MusicRankingPolicyTrainer.class);
    private static final List<String> FEATURES = List.of(
            "semantic", "structured", "rrf", "personal", "freshness", "longtail", "exposurePenalty");

    private final MusicPersonalizationRepository repository;

    public MusicRankingPolicyTrainer(MusicPersonalizationRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${music.personalization.policy-training-cron:0 45 3 * * SUN}")
    public void trainCandidate() {
        List<MusicPersonalizationRepository.LearningEvidence> rows = repository.learningEvidence();
        Set<UUID> exposures = new LinkedHashSet<>();
        rows.forEach(row -> exposures.add(row.exposureId()));
        if (rows.size() < 100 || exposures.size() < 20 || repository.latestCandidateLabels() >= rows.size()) {
            return;
        }
        LocalDateTime started = LocalDateTime.now();
        var baseline = repository.policy("baseline-v1").orElseThrow();
        List<UUID> orderedExposures = rows.stream()
                .sorted(Comparator.comparing(MusicPersonalizationRepository.LearningEvidence::createdAt))
                .map(MusicPersonalizationRepository.LearningEvidence::exposureId).distinct().toList();
        int split = Math.max(1, (int) Math.floor(orderedExposures.size() * 0.8));
        Set<UUID> trainingIds = Set.copyOf(orderedExposures.subList(0, split));
        List<MusicPersonalizationRepository.LearningEvidence> training = rows.stream()
                .filter(row -> trainingIds.contains(row.exposureId())).toList();
        List<MusicPersonalizationRepository.LearningEvidence> validation = rows.stream()
                .filter(row -> !trainingIds.contains(row.exposureId())).toList();
        if (validation.isEmpty()) return;

        Map<String, Double> candidate = fit(training, baseline.coefficients());
        double baselineNdcg = ndcg(validation, baseline.coefficients());
        double candidateNdcg = ndcg(validation, candidate);
        double improvement = baselineNdcg <= 0 ? 0 : (candidateNdcg - baselineNdcg) / baselineNdcg;
        double worstSegmentDrop = worstUserDrop(validation, baseline.coefficients(), candidate);
        boolean passed = improvement >= 0.02 && worstSegmentDrop >= -0.05;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("baselineNdcgAt10", baselineNdcg);
        metrics.put("candidateNdcgAt10", candidateNdcg);
        metrics.put("relativeImprovement", improvement);
        metrics.put("worstUserSegmentDrop", worstSegmentDrop);
        metrics.put("validationEvents", validation.size());
        String version = "candidate-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(started);
        repository.savePolicyCandidate(version, passed ? "PASSED" : "REJECTED", candidate, metrics,
                started, LocalDateTime.now(), rows.size(), exposures.size());
        log.info("Music ranking candidate {} saved with status {}, ndcg improvement={}",
                version, passed ? "PASSED" : "REJECTED", improvement);
    }

    static Map<String, Double> fit(List<MusicPersonalizationRepository.LearningEvidence> rows,
                                   Map<String, Double> baseline) {
        Map<String, Double> weights = new LinkedHashMap<>();
        FEATURES.forEach(feature -> weights.put(feature, baseline.getOrDefault(feature, 0.0)));
        for (int epoch = 0; epoch < 300; epoch++) {
            Map<String, Double> gradient = new HashMap<>();
            for (var row : rows) {
                double target = row.reward() > 0 ? 1 : 0;
                double sampleWeight = Math.max(1, Math.abs(row.reward()));
                double predicted = sigmoid(dot(weights, row.features()));
                for (String feature : FEATURES) {
                    double value = number(row.features().get(feature));
                    gradient.merge(feature, sampleWeight * (predicted - target) * value, Double::sum);
                }
            }
            double learningRate = 0.05 / Math.max(1, rows.size());
            for (String feature : FEATURES) {
                double base = baseline.getOrDefault(feature, 0.0);
                double l2 = 0.01 * (weights.get(feature) - base);
                double updated = weights.get(feature) - learningRate * (gradient.getOrDefault(feature, 0.0) + l2);
                double low = Math.min(base * 0.5, base * 1.5);
                double high = Math.max(base * 0.5, base * 1.5);
                weights.put(feature, Math.max(low, Math.min(high, updated)));
            }
        }
        projectContentWeights(weights, baseline);
        return Map.copyOf(weights);
    }

    private static void projectContentWeights(Map<String, Double> weights, Map<String, Double> baseline) {
        List<String> content = List.of("semantic", "structured", "rrf");
        for (int iteration = 0; iteration < 12; iteration++) {
            double sum = content.stream().mapToDouble(weights::get).sum();
            double difference = 1.0 - sum;
            if (Math.abs(difference) < 1e-10) break;
            Map<String, Double> capacity = new LinkedHashMap<>();
            for (String feature : content) {
                double base = baseline.get(feature);
                double boundary = difference > 0 ? base * 1.5 : base * 0.5;
                capacity.put(feature, Math.max(0, difference > 0
                        ? boundary - weights.get(feature) : weights.get(feature) - boundary));
            }
            double totalCapacity = capacity.values().stream().mapToDouble(Double::doubleValue).sum();
            if (totalCapacity <= 1e-12) break;
            for (String feature : content) {
                double share = Math.min(Math.abs(difference), totalCapacity)
                        * capacity.get(feature) / totalCapacity;
                weights.put(feature, weights.get(feature) + Math.copySign(share, difference));
            }
        }
    }

    static double ndcg(List<MusicPersonalizationRepository.LearningEvidence> rows,
                       Map<String, Double> weights) {
        Map<UUID, List<MusicPersonalizationRepository.LearningEvidence>> groups = new LinkedHashMap<>();
        rows.forEach(row -> groups.computeIfAbsent(row.exposureId(), ignored -> new ArrayList<>()).add(row));
        return groups.values().stream().mapToDouble(group -> ndcgGroup(group, weights)).average().orElse(0);
    }

    private static double ndcgGroup(List<MusicPersonalizationRepository.LearningEvidence> group,
                                    Map<String, Double> weights) {
        List<Double> ranked = group.stream().sorted(Comparator
                        .comparingDouble((MusicPersonalizationRepository.LearningEvidence row) ->
                                dot(weights, row.features())).reversed())
                .limit(10).map(row -> Math.max(0, row.reward())).toList();
        List<Double> ideal = group.stream().map(row -> Math.max(0, row.reward()))
                .sorted(Comparator.reverseOrder()).limit(10).toList();
        double idealDcg = dcg(ideal);
        return idealDcg == 0 ? 0 : dcg(ranked) / idealDcg;
    }

    private static double worstUserDrop(List<MusicPersonalizationRepository.LearningEvidence> rows,
                                        Map<String, Double> baseline, Map<String, Double> candidate) {
        Map<Long, List<MusicPersonalizationRepository.LearningEvidence>> users = new HashMap<>();
        rows.forEach(row -> users.computeIfAbsent(row.userId(), ignored -> new ArrayList<>()).add(row));
        double worst = 0;
        for (List<MusicPersonalizationRepository.LearningEvidence> userRows : users.values()) {
            if (userRows.size() < 10) continue;
            double before = ndcg(userRows, baseline);
            double after = ndcg(userRows, candidate);
            double drop = before <= 0 ? 0 : (after - before) / before;
            worst = Math.min(worst, drop);
        }
        return worst;
    }

    private static double dcg(List<Double> relevance) {
        double result = 0;
        for (int index = 0; index < relevance.size(); index++) {
            result += (Math.pow(2, relevance.get(index)) - 1) / (Math.log(index + 2) / Math.log(2));
        }
        return result;
    }

    private static double dot(Map<String, Double> weights, Map<String, Object> features) {
        return FEATURES.stream().mapToDouble(feature -> weights.getOrDefault(feature, 0.0)
                * number(features.get(feature))).sum();
    }

    private static double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-Math.max(-20, Math.min(20, value))));
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
