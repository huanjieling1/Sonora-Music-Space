package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicTrackBo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class MusicCandidateRanker {
    private static final int MAX_TRACKS_PER_ARTIST = 3;
    private static final Set<String> VERSION_WORDS = Set.of(
            "cover", "karaoke", "tribute", "remix", "live", "instrumental", "sped", "slowed", "nightcore",
            "翻唱", "伴奏", "混音", "现场版", "加速", "降速");

    public List<MusicTrackBo> rank(MusicSearchPlan plan, List<Candidate> candidates, int limit) {
        if (limit <= 0 || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ScoredCandidate> scored = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate != null && candidate.track() != null) {
                scored.add(new ScoredCandidate(candidate, score(plan, candidate)));
            }
        }
        scored.sort(Comparator.comparingInt(ScoredCandidate::score).reversed()
                .thenComparingInt(value -> value.candidate().providerOrder())
                .thenComparingInt(value -> value.candidate().sequence()));

        LinkedHashMap<String, MusicTrackBo> unique = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> artistCounts = new LinkedHashMap<>();
        List<MusicTrackBo> overflow = new ArrayList<>();
        boolean diversifyArtists = plan.intent() == MusicSearchIntent.DISCOVERY
                || plan.intent() == MusicSearchIntent.SIMILAR
                || plan.intent() == MusicSearchIntent.ENTITY_RELATED
                || plan.intent() == MusicSearchIntent.AMBIGUOUS;
        for (ScoredCandidate candidate : scored) {
            MusicTrackBo track = candidate.candidate().track();
            String key = deduplicationKey(track);
            if (unique.containsKey(key)) {
                continue;
            }
            String artistKey = primaryArtistKey(track);
            if (diversifyArtists && artistCounts.getOrDefault(artistKey, 0) >= MAX_TRACKS_PER_ARTIST) {
                overflow.add(track);
                continue;
            }
            unique.put(key, track);
            artistCounts.merge(artistKey, 1, Integer::sum);
            if (unique.size() >= limit) {
                break;
            }
        }
        for (MusicTrackBo track : overflow) {
            if (unique.size() >= limit) break;
            unique.putIfAbsent(deduplicationKey(track), track);
        }
        return List.copyOf(unique.values());
    }

    private int score(MusicSearchPlan plan, Candidate candidate) {
        MusicTrackBo track = candidate.track();
        int score = "audio".equals(track.playbackType()) ? 30 : 0;
        if (track.matchType() != null) {
            score += track.matchType() == com.example.agent.model.bo.MusicMatchType.VERIFIED ? 240 : 40;
            score += (int) Math.round(Math.max(0, Math.min(1, track.relevanceScore())) * 100);
        }
        score += switch (candidate.task().type()) {
            case TRACK_ARTIST -> 35;
            case TRACK, ARTIST, ALBUM, ENTITY -> 25;
            case SIMILAR -> 15;
            default -> 8;
        };

        score += entityScore(track.name(), plan.track(), 180, 100);
        if (!plan.artists().isEmpty()) {
            int artistScore = track.artists().stream()
                    .mapToInt(artist -> entityScore(artist, plan.artists().get(0), 150, 80))
                    .max().orElse(0);
            score += artistScore;
        }
        score += entityScore(track.album(), plan.album(), 110, 55);
        score += tokenOverlapScore(candidate.task().query(), track);

        if (plan.intent() == MusicSearchIntent.EXACT_TRACK && StringUtils.hasText(plan.track())) {
            String requested = normalize(plan.track() + " " + candidate.task().query());
            String title = normalize(track.name());
            for (String versionWord : VERSION_WORDS) {
                String normalizedVersion = normalize(versionWord);
                if (title.contains(normalizedVersion) && !requested.contains(normalizedVersion)) {
                    score -= 35;
                }
            }
        }
        return score;
    }

    private static int entityScore(String actual, String expected, int exact, int contains) {
        if (!StringUtils.hasText(actual) || !StringUtils.hasText(expected)) {
            return 0;
        }
        String normalizedActual = normalize(actual);
        String normalizedExpected = normalize(expected);
        if (normalizedActual.equals(normalizedExpected)) {
            return exact;
        }
        if (normalizedActual.contains(normalizedExpected) || normalizedExpected.contains(normalizedActual)) {
            return contains;
        }
        return 0;
    }

    private static int tokenOverlapScore(String query, MusicTrackBo track) {
        Set<String> expected = tokens(query);
        if (expected.isEmpty()) {
            return 0;
        }
        Set<String> actual = tokens(track.name() + " " + String.join(" ", track.artists()) + " " + track.album());
        long matches = expected.stream().filter(actual::contains).count();
        return (int) Math.round(60.0 * matches / expected.size());
    }

    private static Set<String> tokens(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        if (normalized.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() > 1) {
                result.add(token);
            }
        }
        return result;
    }

    private static String deduplicationKey(MusicTrackBo track) {
        String artist = track.artists().isEmpty() ? "" : track.artists().get(0);
        return normalize(track.name()) + "|" + normalize(artist);
    }

    private static String primaryArtistKey(MusicTrackBo track) {
        String artist = track.artists() == null || track.artists().isEmpty() ? "unknown" : track.artists().get(0);
        return normalize(artist);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public record Candidate(MusicTrackBo track, MusicSearchTask task, int providerOrder, int sequence) {
    }

    private record ScoredCandidate(Candidate candidate, int score) {
    }
}
