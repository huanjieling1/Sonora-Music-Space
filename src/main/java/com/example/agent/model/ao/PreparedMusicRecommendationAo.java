package com.example.agent.model.ao;

import com.example.agent.model.bo.MusicSearchPlan;

import java.util.List;

/**
 * A validated recommendation hand-off. The original command remains the audit and hard-constraint source;
 * profile values are bounded soft hints and never replace it.
 */
public record PreparedMusicRecommendationAo(
        MusicRecommendationAo command,
        MusicSearchPlan proposedPlan,
        String searchSeed,
        List<String> preferredTerms,
        List<String> avoidedTerms,
        String rationale,
        String profileStage,
        boolean profileApplied
) {
    public PreparedMusicRecommendationAo {
        if (command == null) throw new IllegalArgumentException("推荐命令不能为空");
        searchSeed = clean(searchSeed, 120);
        preferredTerms = terms(preferredTerms, 6);
        avoidedTerms = terms(avoidedTerms, 4);
        rationale = clean(rationale, 500);
        profileStage = clean(profileStage, 40);
    }

    private static List<String> terms(List<String> values, int limit) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::strip).distinct().limit(limit).toList();
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) return "";
        String result = value.strip();
        return result.length() <= max ? result : result.substring(0, max).strip();
    }
}
