package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicPreferenceType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MusicMemoryConsolidator {
    private final MusicPersonalizationRepository repository;

    public MusicMemoryConsolidator(MusicPersonalizationRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${music.personalization.consolidation-cron:0 15 3 * * *}")
    public void consolidate() {
        Map<Long, Map<SignalKey, Accumulator>> byUser = new HashMap<>();
        for (var evidence : repository.learningEvidence()) {
            Map<SignalKey, Accumulator> signals = byUser.computeIfAbsent(evidence.userId(), ignored -> new HashMap<>());
            String artist = evidence.track().artists().isEmpty() ? "" : evidence.track().artists().get(0);
            if (!artist.isBlank()) {
                signals.computeIfAbsent(new SignalKey(MusicPreferenceType.ARTIST, artist), ignored -> new Accumulator())
                        .add(evidence.exposureId(), evidence.reward());
            }
            tags(evidence.features()).forEach(tag -> signals
                    .computeIfAbsent(tag, ignored -> new Accumulator())
                    .add(evidence.exposureId(), evidence.reward()));
        }
        byUser.forEach((userId, signals) -> {
            List<MusicPersonalizationRepository.PreferenceRow> inferred = new ArrayList<>();
            signals.forEach((key, value) -> {
                if (value.events < 3 || value.exposures.size() < 2) return;
                double posterior = value.alpha / (value.alpha + value.beta);
                int polarity;
                double confidence;
                if (posterior >= 0.70) {
                    polarity = 1;
                    confidence = posterior;
                } else if (posterior <= 0.30) {
                    polarity = -1;
                    confidence = 1 - posterior;
                } else {
                    return;
                }
                inferred.add(new MusicPersonalizationRepository.PreferenceRow(
                        UUID.randomUUID(), "L2", "GLOBAL", null, key.type().name(), key.value(),
                        polarity, confidence, value.events, value.exposures.size(), "behavior_consolidator",
                        LocalDateTime.now().plusDays(30)));
            });
            repository.replaceInferredPreferences(userId, inferred);
        });
    }

    private static Set<SignalKey> tags(Map<String, Object> features) {
        Object raw = features.get("tags");
        if (!(raw instanceof List<?> values)) return Set.of();
        Set<SignalKey> result = new LinkedHashSet<>();
        values.stream().map(String::valueOf).map(String::strip).filter(value -> !value.isBlank())
                .map(MusicMemoryConsolidator::tagSignal).forEach(result::add);
        return Set.copyOf(result);
    }

    private static SignalKey tagSignal(String raw) {
        int separator = raw.indexOf(':');
        if (separator <= 0) return new SignalKey(MusicPreferenceType.TAG, raw);
        try {
            MusicPreferenceType type = MusicPreferenceType.valueOf(raw.substring(0, separator).toUpperCase());
            if (type == MusicPreferenceType.GENRE || type == MusicPreferenceType.LANGUAGE
                    || type == MusicPreferenceType.MOOD || type == MusicPreferenceType.SCENE
                    || type == MusicPreferenceType.TAG) {
                return new SignalKey(type, raw.substring(separator + 1).strip());
            }
        } catch (IllegalArgumentException ignored) {
            // Unknown prefixes remain ordinary auditable tags.
        }
        return new SignalKey(MusicPreferenceType.TAG, raw);
    }

    private record SignalKey(MusicPreferenceType type, String value) {
    }

    private static final class Accumulator {
        private double alpha = 1;
        private double beta = 1;
        private int events;
        private final Set<UUID> exposures = new HashSet<>();

        void add(UUID exposure, double reward) {
            if (reward > 0) alpha += reward;
            if (reward < 0) beta += -reward;
            events++;
            exposures.add(exposure);
        }
    }
}
