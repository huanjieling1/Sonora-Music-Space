package com.example.agent.service.impl;

import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicHardConstraints;
import com.example.agent.model.bo.MusicIntentHints;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSoftIntent;
import com.example.agent.model.bo.MusicTrackBo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MusicRecommendationNoveltyPolicyTest {
    private static final long USER_ID = 42L;
    private static final UUID CONVERSATION_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Test
    void refreshMovesRecallForwardAndExcludesRecentTracksAcrossProviders() {
        MusicPersonalizationRepository repository = mock(MusicPersonalizationRepository.class);
        MusicExecutionPlan plan = discoveryPlan();
        String fingerprint = MusicRecommendationNoveltyPolicy.fingerprint(plan);
        when(repository.nextBatchSequence(USER_ID, CONVERSATION_ID, fingerprint)).thenReturn(3);
        when(repository.recentExposureTracks(USER_ID, CONVERSATION_ID,
                MusicRecommendationNoveltyPolicy.HISTORY_BATCHES)).thenReturn(List.of(
                new MusicPersonalizationRepository.RecentExposureTrack(
                        "batch-2", MusicTrackIdentity.key(track("qq:1", "勋章", "鹿晗", "qq")), "勋章", "鹿晗"),
                new MusicPersonalizationRepository.RecentExposureTrack(
                        "batch-1", MusicTrackIdentity.key(track("qq:2", "FLY-飞（原版）", "ANU", "qq")),
                        "FLY-飞（原版）", "ANU")));
        MusicRecommendationNoveltyPolicy policy = new MusicRecommendationNoveltyPolicy(repository);

        var context = policy.prepare(new MusicRecommendationAo(
                USER_ID, CONVERSATION_ID, "根据反馈重新推荐", 1, 10, true), plan);
        var filtered = policy.filter(context, List.of(
                track("qq:1", "勋章", "鹿晗", "qq"),
                track("youtube:9", "勋章", "鹿晗", "youtube"),
                track("qq:3", "新歌", "新歌手", "qq")));

        assertThat(context.refresh()).isTrue();
        assertThat(context.batchSequence()).isEqualTo(3);
        assertThat(context.retrievalPage()).isEqualTo(3);
        assertThat(filtered.excludedCount()).isEqualTo(2);
        assertThat(filtered.tracks()).extracting(MusicTrackBo::id).containsExactly("qq:3");
    }

    @Test
    void exactTrackRequestNeverLosesItsHardConstraintEvenIfRefreshFlagIsPresent() {
        MusicPersonalizationRepository repository = mock(MusicPersonalizationRepository.class);
        MusicExecutionPlan exact = new MusicExecutionPlan("播放晴天", MusicSearchIntent.EXACT_TRACK,
                new MusicHardConstraints("晴天", List.of("周杰伦"), null),
                new MusicSoftIntent("", List.of()), new MusicIntentHints(List.of(), List.of(), List.of()),
                List.of(), 1, null);
        String fingerprint = MusicRecommendationNoveltyPolicy.fingerprint(exact);
        when(repository.nextBatchSequence(USER_ID, CONVERSATION_ID, fingerprint)).thenReturn(2);

        var context = new MusicRecommendationNoveltyPolicy(repository).prepare(new MusicRecommendationAo(
                USER_ID, CONVERSATION_ID, "播放晴天", 1, 10, true), exact);

        assertThat(context.refresh()).isFalse();
        assertThat(context.excludedTrackKeys()).isEmpty();
    }

    private static MusicExecutionPlan discoveryPlan() {
        return new MusicExecutionPlan("符合口味的歌", MusicSearchIntent.DISCOVERY,
                new MusicHardConstraints(null, List.of(), null),
                new MusicSoftIntent("发现音乐", List.of()),
                new MusicIntentHints(List.of("pop"), List.of("upbeat"), List.of()),
                List.of(), 0.9, null);
    }

    private static MusicTrackBo track(String id, String title, String artist, String provider) {
        return new MusicTrackBo(id, title, List.of(artist), "Album", "", 180_000,
                "", provider, "audio", "https://audio.test/" + id, "");
    }
}
