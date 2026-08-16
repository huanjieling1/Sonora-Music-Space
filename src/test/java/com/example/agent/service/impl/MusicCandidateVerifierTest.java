package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicHardConstraints;
import com.example.agent.model.bo.MusicIntentHints;
import com.example.agent.model.bo.MusicMatchType;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicTrackRelationBo;
import com.example.agent.model.bo.MusicUnderstandingBo;
import com.example.agent.model.bo.MusicSoftIntent;
import com.example.agent.model.bo.MusicToolCall;
import com.example.agent.model.bo.MusicToolName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicCandidateVerifierTest {
    private final MusicCandidateVerifier verifier = new MusicCandidateVerifier();

    @Test
    void verifiesKnownValorantTrackAndRejectsUnrelatedStrictCandidate() {
        MusicSearchTask task = new MusicSearchTask(MusicSearchTaskType.ENTITY,
                "VALORANT Champions anthem", null, null, null);
        MusicSearchPlan plan = new MusicSearchPlan(MusicSearchIntent.ENTITY_RELATED, null, List.of(), null,
                List.of(), List.of(), List.of(), List.of(task), 1, null);
        MusicUnderstandingBo understanding = new MusicUnderstandingBo(2L, "VALORANT Champions",
                MusicEntityType.EVENT, List.of("无畏契约冠军赛", "VALORANT Champions", "无畏契约"), 1,
                List.of("curated"), List.of("cinematic"),
                List.of(new MusicTrackRelationBo("Die For You", "Grabbitz", null,
                        "OFFICIAL_EVENT_ANTHEM", "官方赛事歌曲", "curated", null, 1)), List.of());
        var candidates = List.of(
                candidate(track("1", "Die For You (为你而战)", "无畏契约 / Grabbitz"), task, 0),
                candidate(track("2", "Believer", "Imagine Dragons"), task, 1));

        var result = verifier.verify(plan, understanding, candidates, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).track().name()).contains("Die For You");
        assertThat(result.get(0).track().matchType()).isEqualTo(MusicMatchType.VERIFIED);
        assertThat(result.get(0).track().relationLabel()).isEqualTo("官方赛事歌曲");
    }

    @Test
    void labelsStyleFillAsRelatedInsteadOfOfficial() {
        MusicSearchTask task = new MusicSearchTask(MusicSearchTaskType.KEYWORDS,
                "cinematic electronic", null, null, null);
        MusicSearchPlan plan = new MusicSearchPlan(MusicSearchIntent.ENTITY_RELATED, null, List.of(), null,
                List.of(), List.of(), List.of(), List.of(task), 1, null);
        MusicUnderstandingBo understanding = new MusicUnderstandingBo(1L, "VALORANT", MusicEntityType.GAME,
                List.of("无畏契约", "VALORANT"), 1, List.of("curated"),
                List.of("cinematic", "electronic"), List.of(), List.of());

        var result = verifier.verify(plan, understanding,
                List.of(candidate(track("2", "Believer", "Imagine Dragons"), task, 0)), true);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.track().matchType()).isEqualTo(MusicMatchType.RELATED);
            assertThat(candidate.track().relationLabel()).contains("风格延伸");
        });
    }

    @Test
    void rejectsWrongTrackFromDirectProviderInsteadOfTrustingProviderRank() {
        MusicSearchTask task = new MusicSearchTask(MusicSearchTaskType.TRACK_ARTIST,
                "Faded Alan Walker", "Faded", "Alan Walker", null);
        MusicExecutionPlan plan = executionPlan(MusicSearchIntent.EXACT_TRACK,
                new MusicHardConstraints("Faded", List.of("Alan Walker"), null), task, List.of());

        var result = verifier.verify(plan, MusicUnderstandingBo.unresolved(), List.of(
                candidate(track("1", "Faded", "Alan Walker"), task, 0),
                candidate(track("2", "Faded Love", "Leony"), task, 1)), false);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.track().id()).isEqualTo("1");
            assertThat(candidate.track().matchType()).isEqualTo(MusicMatchType.VERIFIED);
        });
    }

    @Test
    void appliesNegativeConstraintBeforeRanking() {
        MusicSearchTask task = new MusicSearchTask(MusicSearchTaskType.SCENE,
                "热血摇滚", null, null, null);
        MusicExecutionPlan plan = executionPlan(MusicSearchIntent.DISCOVERY,
                new MusicHardConstraints(null, List.of(), null), task, List.of("翻唱"));

        var result = verifier.verify(plan, MusicUnderstandingBo.unresolved(), List.of(
                candidate(track("1", "热血之歌", "Original Artist"), task, 0),
                candidate(track("2", "热血之歌（翻唱）", "Cover Artist"), task, 1)), false);

        assertThat(result).extracting(candidate -> candidate.track().id()).containsExactly("1");
    }

    private static MusicExecutionPlan executionPlan(MusicSearchIntent intent,
                                                    MusicHardConstraints hard,
                                                    MusicSearchTask task,
                                                    List<String> avoid) {
        return new MusicExecutionPlan(task.query(), intent, hard,
                new MusicSoftIntent(task.query(), avoid),
                new MusicIntentHints(List.of(), List.of(), List.of()),
                List.of(new MusicToolCall("qq_direct", MusicToolName.QQ_DIRECT_SEARCH,
                        List.of(task), List.of())), 1, null);
    }

    private static MusicCandidateRanker.Candidate candidate(MusicTrackBo track, MusicSearchTask task, int sequence) {
        return new MusicCandidateRanker.Candidate(track, task, 1, sequence);
    }

    private static MusicTrackBo track(String id, String name, String artist) {
        return new MusicTrackBo(id, name, List.of(artist), "Album", "https://image", 180000,
                "https://external", "qq", "audio", "https://audio", null);
    }
}
