package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicUnderstandingBo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class MusicSearchPlanGrounder {
    public MusicSearchPlan ground(String description, MusicSearchPlan proposed, MusicUnderstandingBo understanding) {
        if (understanding == null || !understanding.resolved()) {
            return proposed;
        }
        String canonical = understanding.canonicalName();
        String requestAlias = bestRequestAlias(description, understanding.aliases(), canonical);
        List<MusicSearchTask> tasks = new ArrayList<>();
        MusicSearchIntent intent;
        String track = null;
        List<String> artists = List.of();
        String album = null;
        switch (understanding.entityType()) {
            case TRACK -> {
                intent = MusicSearchIntent.EXACT_TRACK;
                track = canonical;
                tasks.add(new MusicSearchTask(MusicSearchTaskType.TRACK, canonical, canonical, null, null));
            }
            case ARTIST -> {
                intent = MusicSearchIntent.ARTIST;
                artists = List.of(canonical);
                tasks.add(new MusicSearchTask(MusicSearchTaskType.ARTIST, canonical, null, canonical, null));
            }
            case ALBUM -> {
                intent = MusicSearchIntent.ALBUM;
                album = canonical;
                tasks.add(new MusicSearchTask(MusicSearchTaskType.ALBUM, canonical, null, null, canonical));
            }
            default -> {
                intent = MusicSearchIntent.ENTITY_RELATED;
                tasks.add(new MusicSearchTask(MusicSearchTaskType.ENTITY, requestAlias, null, null, null));
                String suffix = understanding.entityType() == MusicEntityType.EVENT ? "anthem" : "official music";
                tasks.add(new MusicSearchTask(MusicSearchTaskType.ENTITY, join(canonical, suffix), null, null, null));
                if (understanding.entityType() != MusicEntityType.EVENT) {
                    tasks.add(new MusicSearchTask(MusicSearchTaskType.ENTITY,
                            join(canonical, "soundtrack"), null, null, null));
                }
            }
        }
        List<MusicSearchTask> unique = tasks.stream()
                .filter(task -> StringUtils.hasText(task.query()))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                task -> MusicTextNormalizer.normalize(task.query()), task -> task,
                                (left, right) -> left, java.util.LinkedHashMap::new),
                        values -> List.copyOf(values.values())));
        return new MusicSearchPlan(intent, track, artists, album, List.of(), List.of(), List.of(),
                unique, Math.max(understanding.confidence(), proposed == null ? 0 : proposed.confidence()), null);
    }

    public List<MusicSearchTask> relatedTasks(MusicUnderstandingBo understanding) {
        if (understanding == null || !understanding.resolved()) {
            return List.of();
        }
        List<String> terms = understanding.relatedTerms();
        if (terms == null || terms.isEmpty()) {
            terms = List.of("cinematic", "electronic", "soundtrack");
        }
        List<MusicSearchTask> tasks = new ArrayList<>();
        for (int index = 0; index < terms.size() && tasks.size() < 3; index += 2) {
            String second = index + 1 < terms.size() ? terms.get(index + 1) : "music";
            tasks.add(new MusicSearchTask(MusicSearchTaskType.KEYWORDS,
                    join(terms.get(index), second), null, null, null));
        }
        return List.copyOf(tasks);
    }

    private static String bestRequestAlias(String description, List<String> aliases, String fallback) {
        String normalizedDescription = MusicTextNormalizer.normalize(description);
        return aliases.stream()
                .filter(StringUtils::hasText)
                .filter(alias -> normalizedDescription.contains(MusicTextNormalizer.normalize(alias)))
                .max(java.util.Comparator.comparingInt(String::length))
                .orElse(fallback);
    }

    private static String join(String... values) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) parts.add(value.strip());
        }
        return String.join(" ", parts);
    }
}
