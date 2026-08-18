package com.example.agent.service.impl;

import com.example.agent.config.MusicPersonalizationProperties;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicHardConstraints;
import com.example.agent.model.bo.MusicIntentHints;
import com.example.agent.model.bo.MusicMatchType;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSoftIntent;
import com.example.agent.model.bo.MusicTrackBo;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MusicPersonalizedRankerTest {
    private static final long USER_ID = 42L;
    private static final UUID CONVERSATION_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void boundsPersonalizationAndReservesThompsonExplorationSlots() {
        Fixture fixture = fixture();
        List<MusicTrackBo> candidates = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            candidates.add(track("qq:" + index, "Focus " + index, "Artist " + index));
        }

        var result = fixture.ranker.rank(UUID.fromString("11111111-1111-1111-8111-111111111111"),
                command("适合专注写代码的电子乐"), discoveryPlan(), candidates, Set.of("qq"), 10);

        assertThat(result.tracks()).hasSize(10);
        assertThat(result.status()).isEqualTo(com.example.agent.model.bo.MusicPersonalizationStatus.DEGRADED);
        assertThat(result.tracks().stream().filter(MusicTrackBo::exploration).count()).isEqualTo(2);
        assertThat(result.exposureTracks()).allSatisfy(item ->
                assertThat(number(item.features().get("personalDelta"))).isBetween(-0.08, 0.08));
        assertThat(result.exposureTracks()).allSatisfy(item ->
                assertThat(number(item.features().get("rrf"))).isBetween(0.0, 1.0));
    }

    @Test
    void exactTrackHardConstraintOverridesDislikeAndHistoricalTaste() {
        Fixture fixture = fixture();
        MusicTrackBo target = track("qq:target", "Faded", "Alan Walker");
        when(fixture.repository.explicitDislikedTrackKeys(USER_ID))
                .thenReturn(List.of(MusicTrackIdentity.key(target)));
        when(fixture.repository.effectivePreferences(USER_ID, CONVERSATION_ID)).thenReturn(List.of(
                new MusicPersonalizationRepository.PreferenceRow(UUID.randomUUID(), "L1", "GLOBAL", null,
                        "ARTIST", "Other Artist", 1, 1, 1, 1, "user", null)));
        MusicExecutionPlan exact = new MusicExecutionPlan("播放 Faded", MusicSearchIntent.EXACT_TRACK,
                new MusicHardConstraints("Faded", List.of(), null),
                new MusicSoftIntent("", List.of()), new MusicIntentHints(List.of(), List.of(), List.of()),
                List.of(), 1, null);

        var result = fixture.ranker.rank(UUID.randomUUID(), command("播放 Faded"), exact,
                List.of(track("qq:other", "Other Song", "Other Artist"), target), Set.of("qq"), 10);

        assertThat(result.tracks()).extracting(MusicTrackBo::id).containsExactly("qq:target");
    }

    @Test
    void mmrEnforcesTwoTracksPerArtistForDiscovery() {
        Fixture fixture = fixture();
        List<MusicTrackBo> candidates = List.of(
                track("qq:1", "One", "Same Artist"), track("qq:2", "Two", "Same Artist"),
                track("qq:3", "Three", "Same Artist"), track("qq:4", "Four", "Same Artist"),
                track("qq:5", "Five", "Same Artist"), track("qq:6", "Six", "Another Artist"),
                track("qq:7", "Seven", "Third Artist"));

        var result = fixture.ranker.rank(UUID.randomUUID(), command("发现一些电子乐"), discoveryPlan(),
                candidates, Set.of("qq"), 7);

        assertThat(result.tracks().stream()
                .filter(track -> track.artists().contains("Same Artist")).count()).isEqualTo(2);
    }

    @Test
    void refreshNoveltyExclusionAlsoAppliesInsidePersonalizedAndGraphRanking() {
        Fixture fixture = fixture();
        MusicTrackBo seen = track("qq:seen", "Repeated Song", "Known Artist");
        MusicTrackBo fresh = track("qq:fresh", "Fresh Song", "New Artist");
        var novelty = new MusicRecommendationNoveltyPolicy.Context(
                "fingerprint", 2, true, 2, 1,
                Set.of(MusicTrackIdentity.key(seen)),
                Set.of(MusicTrackIdentity.canonicalKey(seen)));

        var result = fixture.ranker.rank(UUID.randomUUID(), command("换一批"), discoveryPlan(),
                List.of(seen, fresh), Set.of("qq"), 10, novelty);

        assertThat(result.tracks()).extracting(MusicTrackBo::id).containsExactly("qq:fresh");
    }

    private static Fixture fixture() {
        MusicPersonalizationRepository repository = mock(MusicPersonalizationRepository.class);
        MusicEmbeddingClient embeddings = mock(MusicEmbeddingClient.class);
        Neo4jMusicGraphClient graph = mock(Neo4jMusicGraphClient.class);
        when(repository.policy("baseline-v1")).thenReturn(Optional.of(
                new MusicPersonalizationRepository.PolicyRow("baseline-v1", "PASSED", Map.of(
                        "semantic", 0.45, "structured", 0.30, "rrf", 0.25,
                        "personal", 0.06, "freshness", 0.035, "longtail", 0.025,
                        "exposurePenalty", -0.06), 0, 0)));
        when(repository.effectivePreferences(USER_ID, CONVERSATION_ID)).thenReturn(List.of());
        when(repository.recentContextRejections(USER_ID, CONVERSATION_ID)).thenReturn(List.of());
        when(repository.explicitDislikedTrackKeys(USER_ID)).thenReturn(List.of());
        when(repository.trackSignals(USER_ID)).thenReturn(Map.of());
        when(repository.explicitTrackPolarities(USER_ID)).thenReturn(Map.of());
        when(graph.affinity(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Map.of());
        MusicPersonalizationProperties properties = new MusicPersonalizationProperties(true, "baseline-v1",
                new MusicPersonalizationProperties.Embedding(false, "", "", "embedding-3", 512, 8, 64),
                new MusicPersonalizationProperties.Neo4j(false, "", "", "", "neo4j"),
                new MusicPersonalizationProperties.Ranking(60, 0.65, 0.15, 0.7, 2, 0.08));
        return new Fixture(repository, new MusicPersonalizedRanker(properties, repository, embeddings, graph));
    }

    private static MusicRecommendationAo command(String description) {
        return new MusicRecommendationAo(USER_ID, CONVERSATION_ID, description, 1, 10);
    }

    private static MusicExecutionPlan discoveryPlan() {
        return new MusicExecutionPlan("适合专注写代码的电子乐", MusicSearchIntent.DISCOVERY,
                new MusicHardConstraints(null, List.of(), null),
                new MusicSoftIntent("适合专注写代码的电子乐", List.of()),
                new MusicIntentHints(List.of("electronic"), List.of("focused"), List.of("coding")),
                List.of(), 0.95, null);
    }

    private static MusicTrackBo track(String id, String name, String artist) {
        return new MusicTrackBo(id, name, List.of(artist), "Album", "https://image", 120_000,
                "https://external", "qq", "audio", "/api/music/qq/playback/1", null,
                MusicMatchType.RELATED, "曲库候选", 0.6);
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private record Fixture(MusicPersonalizationRepository repository, MusicPersonalizedRanker ranker) {
    }
}
