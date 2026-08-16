package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicTrackBo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicCandidateRankerTest {
    private final MusicCandidateRanker ranker = new MusicCandidateRanker();

    @Test
    void ranksExactTitleAndArtistAboveCoversAndLooseMatches() {
        MusicSearchTask task = new MusicSearchTask(MusicSearchTaskType.TRACK_ARTIST,
                "Faded Alan Walker", "Faded", "Alan Walker", null);
        MusicSearchPlan plan = new MusicSearchPlan(MusicSearchIntent.EXACT_TRACK, "Faded",
                List.of("Alan Walker"), null, List.of(), List.of(), List.of(), List.of(task), 0.98, null);

        var candidates = List.of(
                new MusicCandidateRanker.Candidate(track("1", "Faded cover", "Other Artist"), task, 10, 0),
                new MusicCandidateRanker.Candidate(track("2", "Faded", "Alan Walker"), task, 20, 1),
                new MusicCandidateRanker.Candidate(track("3", "Faded Remix", "Alan Walker"), task, 10, 2));

        assertThat(ranker.rank(plan, candidates, 3)).extracting(MusicTrackBo::id)
                .containsExactly("2", "3", "1");
    }

    @Test
    void deduplicatesNormalizedTitleAndArtistKeepingTheBetterSource() {
        MusicSearchTask task = new MusicSearchTask(MusicSearchTaskType.KEYWORDS,
                "night drive", null, null, null);
        MusicSearchPlan plan = new MusicSearchPlan(MusicSearchIntent.DISCOVERY, null,
                List.of(), null, List.of(), List.of(), List.of(), List.of(task), 0.8, null);

        var candidates = List.of(
                new MusicCandidateRanker.Candidate(track("a", "Night-Drive", "The Artist"), task, 20, 1),
                new MusicCandidateRanker.Candidate(track("b", "Night Drive", "the artist"), task, 10, 0));

        assertThat(ranker.rank(plan, candidates, 5)).extracting(MusicTrackBo::id).containsExactly("b");
    }

    private static MusicTrackBo track(String id, String name, String artist) {
        return new MusicTrackBo(id, name, List.of(artist), "Album", "https://image", 180000,
                "https://external", "jamendo", "audio", "https://audio", null);
    }
}
