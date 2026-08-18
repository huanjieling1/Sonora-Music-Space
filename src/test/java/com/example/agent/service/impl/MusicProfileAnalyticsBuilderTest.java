package com.example.agent.service.impl;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicProfileAnalyticsBuilderTest {
    private final MusicProfileAnalyticsBuilder builder = new MusicProfileAnalyticsBuilder();

    @Test
    void waitsForEnoughPlaysAndUniqueTracksBeforeAssigningLabels() {
        var analytics = builder.build(totals(19, 8, 16, 1), List.of(), List.of(), List.of());

        assertThat(analytics.profileReady()).isFalse();
        assertThat(analytics.labels()).isEmpty();
        assertThat(analytics.requiredPlayCount()).isEqualTo(20);
    }

    @Test
    void createsAuditableArtistTagAndBehaviorLabels() {
        var totals = totals(40, 18, 32, 10);
        var artist = new MusicPersonalizationRepository.ArtistStatRow(
                "周杰伦", 9, 15, 13, 4, 2_400_000, LocalDateTime.now());
        var tag = new MusicPersonalizationRepository.TagStatRow(
                "GENRE", "华语流行", 7, 16, 2_600_000, 42, 0.95);

        var analytics = builder.build(totals, List.of(), List.of(artist), List.of(tag));

        assertThat(analytics.profileReady()).isTrue();
        assertThat(analytics.labels()).extracting(item -> item.name())
                .contains("周杰伦深度听众", "华语流行偏爱者", "单曲循环型听众", "高完播型听众");
        assertThat(analytics.labels()).allSatisfy(label -> {
            assertThat(label.basis()).isNotBlank();
            assertThat(label.confidence()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void labelsAMatureAndDiverseListeningHistoryWithoutRequiringExtremeConcentration() {
        var totals = totals(180, 92, 27, 4);
        var artist = new MusicPersonalizationRepository.ArtistStatRow(
                "Mili", 9, 39, 8, 2, 1_300_000, LocalDateTime.now());

        var analytics = builder.build(totals, List.of(), List.of(artist), List.of());

        assertThat(analytics.labels()).extracting(item -> item.name())
                .contains("Mili深度听众", "新歌探索者");
    }

    private static MusicPersonalizationRepository.ListeningTotals totals(
            long plays, long uniqueTracks, long completes, long repeats) {
        return new MusicPersonalizationRepository.ListeningTotals(uniqueTracks, plays, completes,
                2, repeats, 5_400_000, LocalDateTime.now().minusDays(10), LocalDateTime.now());
    }
}
