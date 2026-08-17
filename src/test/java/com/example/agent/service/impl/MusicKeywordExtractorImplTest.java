package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicKeywordExtractorImplTest {
    private final MusicSearchPlanCompiler compiler = new MusicSearchPlanCompiler();

    @Test
    void keepsRe0InsteadOfUsingModelGeneralizations() {
        var planner = new MusicKeywordExtractorImpl(description -> new MusicSearchPlan(
                MusicSearchIntent.EXACT_TRACK, "Re:从零开始的异世界生活", List.of(), null,
                List.of("J-Pop"), List.of("Epic"), List.of(), List.of(
                new MusicSearchTask(MusicSearchTaskType.ENTITY,
                        "零开始的异世界生活", null, null, null),
                new MusicSearchTask(MusicSearchTaskType.GENRE,
                        "J-Pop Epic", null, null, null)), 0.9, null), compiler);

        var result = planner.extract("找一些Re0的歌");

        assertThat(result.keyword()).isEqualTo("Re0");
    }

    @Test
    void keepsOriginalAcronymCasing() {
        var planner = new MusicKeywordExtractorImpl(description -> new MusicSearchPlan(
                MusicSearchIntent.ENTITY_RELATED, null, List.of(), null,
                List.of(), List.of(), List.of(), List.of(), 0.8, null), compiler);

        assertThat(planner.extract("我想听LoL的歌").keyword()).isEqualTo("LoL");
    }

    @Test
    void extractsOnlyTheNamedSubjectFromAPlaylistRequest() {
        var planner = new MusicKeywordExtractorImpl(description -> new MusicSearchPlan(
                MusicSearchIntent.ENTITY_RELATED, null, List.of(), null,
                List.of(), List.of(), List.of(), List.of(), 0.8, null), compiler);

        assertThat(planner.extract("找一个跟无畏契约相关的歌单给我").keyword()).isEqualTo("无畏契约");
    }
}
