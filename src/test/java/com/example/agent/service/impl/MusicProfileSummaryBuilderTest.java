package com.example.agent.service.impl;

import com.example.agent.model.vo.music.MusicPreferenceVo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicProfileSummaryBuilderTest {
    private final MusicProfileSummaryBuilder builder = new MusicProfileSummaryBuilder();

    @Test
    void doesNotInventPreferencesWhenThereIsNoEvidence() {
        var summary = builder.build(List.of(), 0, 0);

        assertThat(summary.stage()).isEqualTo("EMPTY");
        assertThat(summary.likes()).isEmpty();
        assertThat(summary.avoids()).isEmpty();
        assertThat(summary.overview()).contains("不会凭空猜测");
        assertThat(summary.observations()).anyMatch(note -> note.contains("不会被当作负反馈"));
    }

    @Test
    void prioritizesExplicitPreferencesAndExplainsQualifiedInferences() {
        List<MusicPreferenceVo> preferences = List.of(
                preference("L2", "GENRE", "独立摇滚", 1, 0.84, 5),
                preference("L1", "ARTIST", "Mili", 1, 1.0, 1),
                preference("L1", "MOOD", "悲伤", -1, 1.0, 1),
                preference("L2", "SCENE", "深夜", 1, 0.76, 3));

        var summary = builder.build(preferences, 24, 9);

        assertThat(summary.stage()).isEqualTo("STABLE");
        assertThat(summary.likes()).extracting(item -> item.value()).containsExactly("Mili", "独立摇滚", "深夜");
        assertThat(summary.avoids()).singleElement().satisfies(item -> {
            assertThat(item.value()).isEqualTo("悲伤");
            assertThat(item.basis()).isEqualTo("用户明确设置");
        });
        assertThat(summary.observations()).anyMatch(note -> note.contains("门槛"));
    }

    @Test
    void remainsInColdStartUntilEvidenceComesFromEnoughFeedbackAndExposures() {
        var summary = builder.build(List.of(preference("L1", "GENRE", "爵士", 1, 1.0, 1)), 1, 1);

        assertThat(summary.stage()).isEqualTo("COLD_START");
        assertThat(summary.confidenceLabel()).isEqualTo("较低");
    }

    private static MusicPreferenceVo preference(String layer, String type, String value, int polarity,
                                                 double confidence, int evidenceCount) {
        return new MusicPreferenceVo(UUID.randomUUID(), layer, "GLOBAL", type, value, polarity,
                confidence, evidenceCount, layer.equals("L1") ? "manual" : "behavior",
                layer.equals("L2") ? LocalDateTime.now().plusDays(30) : null);
    }
}
