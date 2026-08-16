package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicTrackBo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicRankingPolicyTrainerTest {
    @Test
    void learnedWeightsStayOnTheBoundedContentSimplex() {
        Map<String, Double> baseline = Map.of(
                "semantic", 0.45, "structured", 0.30, "rrf", 0.25,
                "personal", 0.06, "freshness", 0.035, "longtail", 0.025,
                "exposurePenalty", -0.06);
        List<MusicPersonalizationRepository.LearningEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            evidence.add(new MusicPersonalizationRepository.LearningEvidence(1L, UUID.randomUUID(), "LIKE", 2,
                    "track-" + index, track(index), Map.of(
                    "semantic", 1.0, "structured", index % 2 == 0 ? 0.0 : 0.2,
                    "rrf", 0.1, "personal", 1.0, "freshness", 1.0,
                    "longtail", 1.0, "exposurePenalty", 0.0), LocalDateTime.now()));
        }

        Map<String, Double> learned = MusicRankingPolicyTrainer.fit(evidence, baseline);

        assertThat(learned.get("semantic") + learned.get("structured") + learned.get("rrf"))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
        baseline.forEach((feature, value) -> {
            double low = Math.min(value * 0.5, value * 1.5);
            double high = Math.max(value * 0.5, value * 1.5);
            assertThat(learned.get(feature)).isBetween(low, high);
        });
    }

    private static MusicTrackBo track(int index) {
        return new MusicTrackBo("qq:" + index, "Track " + index, List.of("Artist"), "Album",
                "https://image", 120_000, "https://external", "qq", "audio", "https://audio", null);
    }
}
