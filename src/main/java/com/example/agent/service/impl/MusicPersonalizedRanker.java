package com.example.agent.service.impl;

import com.example.agent.config.MusicPersonalizationProperties;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.MusicMatchType;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicTrackBo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Component
public class MusicPersonalizedRanker {
    private static final Logger log = LoggerFactory.getLogger(MusicPersonalizedRanker.class);

    private final MusicPersonalizationProperties properties;
    private final MusicPersonalizationRepository repository;
    private final MusicEmbeddingClient embeddings;
    private final Neo4jMusicGraphClient graph;

    public MusicPersonalizedRanker(MusicPersonalizationProperties properties,
                                   MusicPersonalizationRepository repository,
                                   MusicEmbeddingClient embeddings,
                                   Neo4jMusicGraphClient graph) {
        this.properties = properties;
        this.repository = repository;
        this.embeddings = embeddings;
        this.graph = graph;
    }

    public RankingResult rank(UUID exposureId, MusicRecommendationAo command, MusicExecutionPlan plan,
                              List<MusicTrackBo> catalogCandidates, Set<String> availableProviders,
                              int limit) {
        if (!properties.enabled() || !command.personalizedRequest()) {
            List<MusicTrackBo> tracks = catalogCandidates.stream().limit(limit)
                    .map(track -> baselineReason(track, plan)).toList();
            return new RankingResult(tracks, exposureTracks(tracks), "baseline-v1",
                    MusicPersonalizationStatus.DISABLED);
        }

        repository.requireOwnedConversation(command.userId(), command.conversationId());
        var policy = repository.policy(properties.activePolicyVersion())
                .orElseThrow(() -> new IllegalStateException("Baseline music ranking policy is missing"));
        List<MusicPersonalizationRepository.PreferenceRow> preferences =
                repository.effectivePreferences(command.userId(), command.conversationId());
        Set<String> contextRejected = new HashSet<>(
                repository.recentContextRejections(command.userId(), command.conversationId()));
        Set<String> dislikedKeys = new HashSet<>(repository.explicitDislikedTrackKeys(command.userId()));
        Map<String, MusicPersonalizationRepository.TrackSignal> signals = repository.trackSignals(command.userId());
        Map<String, Integer> explicitTrackPolarities = repository.explicitTrackPolarities(command.userId());
        List<String> requestTags = requestTags(plan);

        LinkedHashMap<String, CandidateState> candidates = new LinkedHashMap<>();
        int catalogRank = 0;
        for (MusicTrackBo track : catalogCandidates) {
            CandidateState state = candidates.computeIfAbsent(MusicTrackIdentity.key(track),
                    key -> new CandidateState(key, track));
            state.sourceRanks.putIfAbsent("catalog", ++catalogRank);
            state.requestTags.addAll(requestTags);
            state.contentTags.addAll(requestTags);
        }

        boolean embeddingHealthy = false;
        List<Double> queryVector = List.of();
        if (embeddings.configured() && !candidates.isEmpty()) {
            try {
                Map<String, Neo4jMusicGraphClient.CachedEmbedding> cached = graph.ready()
                        ? graph.embeddingVectors(new ArrayList<>(candidates.keySet())) : Map.of();
                List<String> inputs = new ArrayList<>();
                inputs.add(command.description());
                List<CandidateState> missing = new ArrayList<>();
                for (CandidateState state : candidates.values()) {
                    String contentText = MusicTrackIdentity.contentText(state.track, state.contentTags);
                    var stored = cached.get(state.trackKey);
                    if (stored != null && MusicTrackIdentity.sha256(contentText).equals(stored.contentHash())
                            && stored.vector().size() == embeddings.dimensions()) {
                        state.semanticVector = stored.vector();
                    } else {
                        missing.add(state);
                        inputs.add(contentText);
                    }
                }
                List<List<Double>> vectors = embeddings.embed(inputs);
                queryVector = vectors.get(0);
                int index = 1;
                for (CandidateState state : missing) state.semanticVector = vectors.get(index++);
                for (CandidateState state : candidates.values()) {
                    state.semanticScore = MusicEmbeddingClient.cosine(queryVector, state.semanticVector);
                }
                embeddingHealthy = true;
            } catch (RuntimeException exception) {
                log.warn("Music embedding failed; current request uses catalog and graph signals: {}",
                        exception.getClass().getSimpleName());
            }
        }

        if (graph.ready() && command.page() == 1) {
            int graphRank = 0;
            for (var recalled : graph.tagRecall(requestTags, Math.max(20, limit * 3))) {
                if (!availableProviders.contains(recalled.track().provider())) continue;
                CandidateState state = candidates.computeIfAbsent(recalled.trackKey(),
                        key -> new CandidateState(key, recalled.track()));
                state.sourceRanks.putIfAbsent("graph", ++graphRank);
                state.contentTags.addAll(recalled.tags());
                state.graphScore = Math.max(state.graphScore, recalled.score());
            }
            if (!queryVector.isEmpty()) {
                int denseRank = 0;
                for (var recalled : graph.vectorRecall(queryVector, Math.max(20, limit * 3))) {
                    if (!availableProviders.contains(recalled.track().provider())) continue;
                    CandidateState state = candidates.computeIfAbsent(recalled.trackKey(),
                            key -> new CandidateState(key, recalled.track()));
                    state.sourceRanks.putIfAbsent("dense", ++denseRank);
                    state.contentTags.addAll(recalled.tags());
                    state.semanticScore = Math.max(state.semanticScore, recalled.score());
                }
            }
        }

        if (embeddingHealthy) {
            List<CandidateState> semantic = candidates.values().stream()
                    .filter(state -> state.semanticScore > 0)
                    .sorted(Comparator.comparingDouble((CandidateState state) -> state.semanticScore).reversed())
                    .toList();
            int denseRank = 0;
            for (CandidateState state : semantic) state.sourceRanks.putIfAbsent("dense", ++denseRank);
        }

        Map<String, Double> affinity = graph.affinity(command.userId(), new ArrayList<>(candidates.keySet()));
        candidates.forEach((key, state) -> state.graphAffinity = affinity.getOrDefault(key, 0.0));

        boolean exactTrack = plan.intent() == MusicSearchIntent.EXACT_TRACK
                && plan.hardConstraints().track() != null;
        List<CandidateState> eligible = candidates.values().stream()
                .filter(state -> !contextRejected.contains(state.track.id()))
                .filter(state -> !dislikedKeys.contains(state.trackKey)
                        || (exactTrack && entityMatches(state.track.name(), plan.hardConstraints().track())))
                .toList();
        eligible = applyHardFilters(eligible, plan);
        applyRrf(eligible, plan.intent());
        score(eligible, policy, preferences, explicitTrackPolarities, signals, plan);

        eligible = eligible.stream().sorted(Comparator.comparingDouble((CandidateState state) -> state.finalScore)
                .reversed()).toList();
        List<CandidateState> explored = addExploration(exposureId, eligible, signals, limit);
        List<CandidateState> selected = mmr(explored, plan, limit);

        MusicPersonalizationStatus status;
        if (!graph.ready() || !graph.vectorReady() || !embeddingHealthy) {
            status = MusicPersonalizationStatus.DEGRADED;
        }
        else if (preferences.isEmpty() && repository.profileStats(command.userId()).labeledEvents() == 0) {
            status = MusicPersonalizationStatus.COLD_START;
        } else status = MusicPersonalizationStatus.ACTIVE;

        List<MusicTrackBo> tracks = selected.stream().map(this::annotatedTrack).toList();
        List<MusicPersonalizationRepository.ExposureTrack> exposureTracks = selected.stream()
                .map(this::exposureTrack).toList();
        rememberContext(command, plan);
        return new RankingResult(tracks, exposureTracks, policy.version(), status);
    }

    private void applyRrf(List<CandidateState> candidates, MusicSearchIntent intent) {
        Map<String, Double> weights = recallWeights(intent);
        int k = properties.ranking().rrfK();
        for (CandidateState state : candidates) {
            state.rrfScore = state.sourceRanks.entrySet().stream()
                    .mapToDouble(entry -> weights.getOrDefault(entry.getKey(), 1.0) / (k + entry.getValue()))
                    .sum();
        }
        normalize(candidates, state -> state.rrfScore, (state, value) -> state.normalizedRrf = value);
    }

    private void score(List<CandidateState> candidates, MusicPersonalizationRepository.PolicyRow policy,
                       List<MusicPersonalizationRepository.PreferenceRow> preferences,
                       Map<String, Integer> explicitTrackPolarities,
                       Map<String, MusicPersonalizationRepository.TrackSignal> signals,
                       MusicExecutionPlan plan) {
        Map<String, Double> coefficients = policy.coefficients();
        LocalDateTime now = LocalDateTime.now();
        for (CandidateState state : candidates) {
            state.structuredScore = structuredScore(state.track, plan);
            if (state.semanticScore == 0) state.semanticScore = 0.5;
            state.personalScore = Math.max(-1, Math.min(1,
                    personalScore(state, preferences) + explicitTrackPolarities.getOrDefault(state.trackKey, 0)));
            MusicPersonalizationRepository.TrackSignal signal = signals.get(state.trackKey);
            double effectiveExposure = 0;
            if (signal != null) {
                double ageDays = Math.max(0, Duration.between(signal.lastExposedAt(), now).toHours() / 24.0);
                effectiveExposure = signal.exposureCount() * Math.pow(0.5, ageDays / 7.0);
            }
            state.exposurePenalty = effectiveExposure / (effectiveExposure + 3.0);
            state.longtailScore = 1.0 / (1.0 + effectiveExposure);
            state.freshnessScore = signal == null ? 1.0 : Math.pow(0.5,
                    Math.max(0, Duration.between(signal.lastExposedAt(), now).toHours() / 24.0) / 21.0);

            double content = coefficient(coefficients, "semantic", 0.45) * state.semanticScore
                    + coefficient(coefficients, "structured", 0.30) * state.structuredScore
                    + coefficient(coefficients, "rrf", 0.25) * state.normalizedRrf;
            double delta = coefficient(coefficients, "personal", 0.06) * state.personalScore
                    + coefficient(coefficients, "freshness", 0.035) * state.freshnessScore
                    + coefficient(coefficients, "longtail", 0.025) * state.longtailScore
                    + coefficient(coefficients, "exposurePenalty", -0.06) * state.exposurePenalty;
            double limit = properties.ranking().personalizationDeltaLimit();
            state.personalDelta = Math.max(-limit, Math.min(limit, delta));
            state.finalScore = Math.max(0, Math.min(1, content + state.personalDelta));
            reasons(state);
        }
    }

    private List<CandidateState> addExploration(UUID exposureId, List<CandidateState> ranked,
                                                Map<String, MusicPersonalizationRepository.TrackSignal> signals,
                                                int limit) {
        if (ranked.size() <= limit) return new ArrayList<>(ranked);
        int coarseSize = Math.max(limit, (int) Math.ceil(ranked.size() * properties.ranking().coarseCutRatio()));
        coarseSize = Math.min(coarseSize, ranked.size());
        List<CandidateState> result = new ArrayList<>(ranked.subList(0, coarseSize));
        List<CandidateState> tail = new ArrayList<>(ranked.subList(coarseSize, ranked.size()));
        int explorationCount = Math.min(tail.size(), Math.max(1,
                (int) Math.ceil(limit * properties.ranking().explorationRatio())));
        Random random = new Random(exposureId.getMostSignificantBits() ^ exposureId.getLeastSignificantBits());
        tail.forEach(state -> {
            var signal = signals.get(state.trackKey);
            double alpha = 1 + (signal == null ? 0 : signal.positiveReward());
            double beta = 1 + (signal == null ? 0 : signal.negativeReward() + signal.exposureCount());
            state.explorationScore = sampleBeta(alpha, beta, random)
                    + 0.12 * state.freshnessScore + 0.08 * state.longtailScore
                    - 0.10 * state.exposurePenalty;
        });
        tail.stream().sorted(Comparator.comparingDouble((CandidateState state) -> state.explorationScore).reversed())
                .limit(explorationCount).forEach(state -> {
                    state.exploration = true;
                    state.reasonCodes.add("EXPLORATION");
                    result.add(state);
                });
        result.sort(Comparator.comparingDouble((CandidateState state) -> state.finalScore).reversed());
        return result;
    }

    private List<CandidateState> mmr(List<CandidateState> ranked, MusicExecutionPlan plan, int limit) {
        if (ranked.isEmpty()) return List.of();
        boolean artistRequest = plan.intent() == MusicSearchIntent.ARTIST;
        List<CandidateState> remaining = new ArrayList<>(ranked);
        List<CandidateState> selected = new ArrayList<>();
        Map<String, Integer> artistCounts = new HashMap<>();
        int explorationTarget = Math.min((int) ranked.stream().filter(state -> state.exploration).count(),
                Math.max(1, (int) Math.ceil(limit * properties.ranking().explorationRatio())));
        selectMmr(remaining, selected, artistCounts, limit - explorationTarget, artistRequest, false);
        selectMmr(remaining, selected, artistCounts, limit, artistRequest, true);
        selectMmr(remaining, selected, artistCounts, limit, artistRequest, false);
        if (selected.size() < limit) {
            for (CandidateState item : ranked) {
                if (selected.size() >= limit) break;
                String artist = primaryArtist(item.track);
                if (selected.contains(item) || (!artistRequest && artistCounts.getOrDefault(artist, 0)
                        >= properties.ranking().maxTracksPerArtist())) continue;
                selected.add(item);
                artistCounts.merge(artist, 1, Integer::sum);
            }
        }
        return List.copyOf(selected);
    }

    private void selectMmr(List<CandidateState> remaining, List<CandidateState> selected,
                           Map<String, Integer> artistCounts, int targetSize,
                           boolean artistRequest, boolean explorationOnly) {
        while (!remaining.isEmpty() && selected.size() < targetSize) {
            CandidateState best = null;
            double bestMmr = -Double.MAX_VALUE;
            for (CandidateState candidate : remaining) {
                if (explorationOnly && !candidate.exploration) continue;
                if (!explorationOnly && candidate.exploration) continue;
                String artist = primaryArtist(candidate.track);
                if (!artistRequest && artistCounts.getOrDefault(artist, 0)
                        >= properties.ranking().maxTracksPerArtist()) continue;
                double overlap = selected.stream().mapToDouble(item -> overlap(candidate, item)).max().orElse(0);
                double mmr = properties.ranking().mmrLambda() * candidate.finalScore
                        - (1 - properties.ranking().mmrLambda()) * overlap;
                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    best = candidate;
                }
            }
            if (best == null) break;
            selected.add(best);
            remaining.remove(best);
            artistCounts.merge(primaryArtist(best.track), 1, Integer::sum);
        }
    }

    private double personalScore(CandidateState state,
                                 List<MusicPersonalizationRepository.PreferenceRow> preferences) {
        double score = state.graphAffinity;
        String metadata = MusicTextNormalizer.normalize(state.track.name() + " "
                + String.join(" ", state.track.artists()) + " " + state.track.album());
        Set<String> tags = new HashSet<>();
        state.contentTags.stream().map(MusicTextNormalizer::normalize).forEach(tags::add);
        Set<String> resolvedPreferenceKeys = new HashSet<>();
        for (var preference : preferences) {
            String preferenceKey = preference.type() + "|" + MusicTextNormalizer.normalize(preference.value());
            if (!resolvedPreferenceKeys.add(preferenceKey)) continue;
            double layerWeight = switch (preference.layer()) {
                case "L1" -> 1.0;
                case "L3" -> 0.8;
                default -> 0.6 * preference.confidence();
            };
            boolean match = switch (preference.type()) {
                case "TRACK" -> MusicTrackIdentity.normalize(preference.value()).equals(state.trackKey)
                        || metadata.contains(MusicTextNormalizer.normalize(preference.value()));
                case "ARTIST" -> state.track.artists().stream()
                        .anyMatch(artist -> entityMatches(artist, preference.value()));
                default -> tags.contains(MusicTextNormalizer.normalize(preference.value()))
                        || metadata.contains(MusicTextNormalizer.normalize(preference.value()));
            };
            if (match) score += preference.polarity() * layerWeight;
        }
        return Math.max(-1, Math.min(1, score));
    }

    private void reasons(CandidateState state) {
        if (state.sourceRanks.containsKey("catalog")) state.reasonCodes.add("CATALOG_MATCH");
        if (state.sourceRanks.containsKey("graph")) state.reasonCodes.add("GRAPH_MATCH");
        if (state.sourceRanks.containsKey("dense")) state.reasonCodes.add("SEMANTIC_MATCH");
        if (state.personalScore > 0.15) state.reasonCodes.add("YOUR_TASTE");
        if (state.personalScore < -0.15) state.reasonCodes.add("TASTE_CONFLICT");
        if (state.exposurePenalty > 0.5) state.reasonCodes.add("RECENTLY_EXPOSED");
        if (state.longtailScore > 0.8) state.reasonCodes.add("FRESH_DISCOVERY");
    }

    private MusicTrackBo annotatedTrack(CandidateState state) {
        List<String> phrases = new ArrayList<>();
        if (state.reasonCodes.contains("SEMANTIC_MATCH")) phrases.add("与你当前描述语义接近");
        if (state.reasonCodes.contains("GRAPH_MATCH")) phrases.add("音乐标签关系匹配");
        if (state.reasonCodes.contains("YOUR_TASTE")) phrases.add("符合你的已知偏好");
        if (state.reasonCodes.contains("FRESH_DISCOVERY")) phrases.add("近期较少出现");
        if (state.exploration) phrases.add("探索位发现");
        if (phrases.isEmpty()) phrases.add("符合当前曲库检索条件");
        return state.track.withRecommendationReason(List.copyOf(state.reasonCodes),
                String.join("；", phrases), state.exploration, state.finalScore);
    }

    private MusicPersonalizationRepository.ExposureTrack exposureTrack(CandidateState state) {
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("semantic", state.semanticScore);
        features.put("structured", state.structuredScore);
        features.put("rrf", state.normalizedRrf);
        features.put("personal", state.personalScore);
        features.put("freshness", state.freshnessScore);
        features.put("longtail", state.longtailScore);
        features.put("exposurePenalty", state.exposurePenalty);
        features.put("personalDelta", state.personalDelta);
        features.put("tags", List.copyOf(state.requestTags));
        return new MusicPersonalizationRepository.ExposureTrack(annotatedTrack(state), state.sourceRanks,
                features, List.copyOf(state.reasonCodes), List.copyOf(state.contentTags),
                state.finalScore, state.exploration);
    }

    private void rememberContext(MusicRecommendationAo command, MusicExecutionPlan plan) {
        List<MusicPersonalizationRepository.ContextPreference> context = new ArrayList<>();
        plan.hints().genres().forEach(value -> context.add(new MusicPersonalizationRepository.ContextPreference(
                MusicPreferenceType.GENRE, value, 1)));
        plan.hints().moods().forEach(value -> context.add(new MusicPersonalizationRepository.ContextPreference(
                MusicPreferenceType.MOOD, value, 1)));
        plan.hints().scenes().forEach(value -> context.add(new MusicPersonalizationRepository.ContextPreference(
                MusicPreferenceType.SCENE, value, 1)));
        plan.hints().languages().forEach(value -> context.add(new MusicPersonalizationRepository.ContextPreference(
                MusicPreferenceType.LANGUAGE, value, 1)));
        repository.rememberConversationContext(command.userId(), command.conversationId(), context);
    }

    private static MusicTrackBo baselineReason(MusicTrackBo track, MusicExecutionPlan plan) {
        return track.withRecommendationReason(List.of("CATALOG_MATCH"), "符合当前曲库检索条件", false,
                track.relevanceScore());
    }

    private static List<MusicPersonalizationRepository.ExposureTrack> exposureTracks(List<MusicTrackBo> tracks) {
        List<MusicPersonalizationRepository.ExposureTrack> result = new ArrayList<>();
        int rank = 0;
        for (MusicTrackBo track : tracks) {
            result.add(new MusicPersonalizationRepository.ExposureTrack(track, Map.of("catalog", ++rank),
                    Map.of("semantic", 0.5, "structured", track.relevanceScore(), "rrf", 1.0,
                            "tags", List.of()), track.reasonCodes(), List.of(),
                    track.relevanceScore(), false));
        }
        return List.copyOf(result);
    }

    private static double structuredScore(MusicTrackBo track, MusicExecutionPlan plan) {
        double base = track.matchType() == MusicMatchType.VERIFIED ? 0.9
                : Math.max(0.45, track.relevanceScore());
        String metadata = MusicTextNormalizer.normalize(track.name() + " "
                + String.join(" ", track.artists()) + " " + track.album());
        List<String> expected = new ArrayList<>();
        if (plan.hardConstraints().track() != null) expected.add(plan.hardConstraints().track());
        expected.addAll(plan.hardConstraints().artists());
        if (plan.hardConstraints().album() != null) expected.add(plan.hardConstraints().album());
        long matches = expected.stream().map(MusicTextNormalizer::normalize)
                .filter(value -> !value.isBlank() && metadata.contains(value)).count();
        if (!expected.isEmpty()) base = Math.max(base, (double) matches / expected.size());
        return Math.max(0, Math.min(1, base));
    }

    private static Map<String, Double> recallWeights(MusicSearchIntent intent) {
        return switch (intent) {
            case EXACT_TRACK, ARTIST, ALBUM, ENTITY_RELATED ->
                    Map.of("catalog", 1.6, "graph", 1.4, "dense", 0.5);
            case SIMILAR -> Map.of("catalog", 0.8, "graph", 1.0, "dense", 1.6);
            case DISCOVERY -> Map.of("catalog", 1.0, "graph", 1.2, "dense", 1.45);
            case AMBIGUOUS -> Map.of("catalog", 1.3, "graph", 1.1, "dense", 0.8);
        };
    }

    private static List<String> requestTags(MusicExecutionPlan plan) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.addAll(plan.hints().genres());
        tags.addAll(plan.hints().moods());
        tags.addAll(plan.hints().scenes());
        tags.addAll(plan.hints().languages());
        return List.copyOf(tags);
    }

    private static List<CandidateState> applyHardFilters(List<CandidateState> candidates,
                                                          MusicExecutionPlan plan) {
        List<CandidateState> allowed = candidates.stream().filter(state -> {
            String metadata = MusicTextNormalizer.normalize(state.track.name() + " "
                    + String.join(" ", state.track.artists()) + " " + state.track.album());
            return plan.softIntent().avoid().stream().map(MusicTextNormalizer::normalize)
                    .filter(value -> !value.isBlank()).noneMatch(metadata::contains);
        }).toList();
        List<CandidateState> exact = switch (plan.intent()) {
            case EXACT_TRACK -> filterMatching(allowed, state -> entityMatches(
                    state.track.name(), plan.hardConstraints().track()));
            case ARTIST -> filterMatching(allowed, state -> state.track.artists().stream()
                    .anyMatch(artist -> plan.hardConstraints().artists().stream()
                            .anyMatch(wanted -> entityMatches(artist, wanted))));
            case ALBUM -> filterMatching(allowed, state -> entityMatches(
                    state.track.album(), plan.hardConstraints().album()));
            default -> List.of();
        };
        return exact.isEmpty() ? allowed : exact;
    }

    private static List<CandidateState> filterMatching(List<CandidateState> candidates,
                                                        java.util.function.Predicate<CandidateState> predicate) {
        return candidates.stream().filter(predicate).toList();
    }

    private static double overlap(CandidateState left, CandidateState right) {
        if (primaryArtist(left.track).equals(primaryArtist(right.track))) return 1.0;
        Set<String> leftTags = new HashSet<>(left.contentTags);
        Set<String> rightTags = new HashSet<>(right.contentTags);
        if (leftTags.isEmpty() || rightTags.isEmpty()) return 0;
        Set<String> union = new HashSet<>(leftTags);
        union.addAll(rightTags);
        leftTags.retainAll(rightTags);
        return (double) leftTags.size() / union.size();
    }

    private static String primaryArtist(MusicTrackBo track) {
        return track.artists().isEmpty() ? "unknown" : MusicTextNormalizer.normalize(track.artists().get(0));
    }

    private static boolean entityMatches(String actual, String expected) {
        if (actual == null || expected == null) return false;
        String left = MusicTextNormalizer.normalize(actual);
        String right = MusicTextNormalizer.normalize(expected);
        return !left.isBlank() && !right.isBlank()
                && (left.equals(right) || left.contains(right) || right.contains(left));
    }

    private static double coefficient(Map<String, Double> values, String key, double fallback) {
        return values.getOrDefault(key, fallback);
    }

    private interface Getter {
        double get(CandidateState state);
    }

    private interface Setter {
        void set(CandidateState state, double value);
    }

    private static void normalize(List<CandidateState> values, Getter getter, Setter setter) {
        if (values.isEmpty()) return;
        double min = values.stream().mapToDouble(getter::get).min().orElse(0);
        double max = values.stream().mapToDouble(getter::get).max().orElse(0);
        if (Math.abs(max - min) < 1e-12) {
            values.forEach(value -> setter.set(value, 0.5));
            return;
        }
        values.forEach(value -> setter.set(value, (getter.get(value) - min) / (max - min)));
    }

    private static double sampleBeta(double alpha, double beta, Random random) {
        double left = sampleGamma(Math.max(0.1, alpha), random);
        double right = sampleGamma(Math.max(0.1, beta), random);
        return left + right == 0 ? 0.5 : left / (left + right);
    }

    private static double sampleGamma(double shape, Random random) {
        if (shape < 1) {
            return sampleGamma(shape + 1, random) * Math.pow(random.nextDouble(), 1 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1 / Math.sqrt(9 * d);
        while (true) {
            double x = random.nextGaussian();
            double v = 1 + c * x;
            if (v <= 0) continue;
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1 - 0.0331 * x * x * x * x
                    || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) return d * v;
        }
    }

    public record RankingResult(List<MusicTrackBo> tracks,
                                List<MusicPersonalizationRepository.ExposureTrack> exposureTracks,
                                String policyVersion, MusicPersonalizationStatus status) {
    }

    private static final class CandidateState {
        private final String trackKey;
        private final MusicTrackBo track;
        private final Map<String, Integer> sourceRanks = new LinkedHashMap<>();
        private final Set<String> contentTags = new LinkedHashSet<>();
        private final Set<String> requestTags = new LinkedHashSet<>();
        private final Set<String> reasonCodes = new LinkedHashSet<>();
        private double semanticScore;
        private List<Double> semanticVector = List.of();
        private double structuredScore;
        private double graphScore;
        private double graphAffinity;
        private double rrfScore;
        private double normalizedRrf;
        private double personalScore;
        private double freshnessScore;
        private double longtailScore;
        private double exposurePenalty;
        private double personalDelta;
        private double finalScore;
        private double explorationScore;
        private boolean exploration;

        private CandidateState(String trackKey, MusicTrackBo track) {
            this.trackKey = trackKey;
            this.track = track;
        }
    }
}
