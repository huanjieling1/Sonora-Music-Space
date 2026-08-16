package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicToolName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicSearchPlanCompilerTest {
    private final MusicSearchPlanCompiler compiler = new MusicSearchPlanCompiler();

    @Test
    void compilesExactTrackIntoHardConstraintsAndClosedToolSequence() {
        MusicSearchPlan proposed = new MusicSearchPlan(MusicSearchIntent.EXACT_TRACK, "Faded",
                List.of("Alan Walker"), null, List.of(), List.of(), List.of(),
                List.of(new MusicSearchTask(MusicSearchTaskType.TRACK_ARTIST,
                        "Faded Alan Walker", "Faded", "Alan Walker", null)), 0.96, null);

        var result = compiler.compile("播放 Alan Walker 的 Faded", proposed);

        assertThat(result.hardConstraints().track()).isEqualTo("Faded");
        assertThat(result.hardConstraints().artists()).containsExactly("Alan Walker");
        assertThat(result.toolCalls()).extracting(call -> call.name()).containsExactly(
                MusicToolName.QQ_DIRECT_SEARCH,
                MusicToolName.OPEN_CATALOG_SEARCH,
                MusicToolName.VIDEO_FALLBACK_SEARCH);
        assertThat(result.tool(MusicToolName.QQ_DIRECT_SEARCH).orElseThrow().tasks().get(0).query())
                .isEqualTo("Faded Alan Walker");
    }

    @Test
    void rejectsModelInventedOfficialAndSoundtrackExpansions() {
        MusicSearchPlan proposed = new MusicSearchPlan(MusicSearchIntent.ENTITY_RELATED, null,
                List.of(), null, List.of(), List.of(), List.of(), List.of(
                new MusicSearchTask(MusicSearchTaskType.ENTITY, "进击的巨人", null, null, null),
                new MusicSearchTask(MusicSearchTaskType.ENTITY, "Attack on Titan official music", null, null, null),
                new MusicSearchTask(MusicSearchTaskType.ENTITY, "Attack on Titan soundtrack", null, null, null)),
                0.84, null);

        var result = compiler.compile("我想要找到进击的巨人的歌曲", proposed);

        assertThat(result.tool(MusicToolName.QQ_DIRECT_SEARCH).orElseThrow().tasks())
                .extracting(MusicSearchTask::query).containsExactly("进击的巨人");
        assertThat(result.tool(MusicToolName.QQ_EXPANDED_SEARCH)).isEmpty();
    }

    @Test
    void keepsExplicitEventQualifierAndCanApplyUserCorrection() {
        MusicSearchPlan proposed = new MusicSearchPlan(MusicSearchIntent.ENTITY_RELATED, null,
                List.of(), null, List.of(), List.of(), List.of(), List.of(
                new MusicSearchTask(MusicSearchTaskType.ENTITY,
                        "VALORANT Champions anthem", null, null, null)), 0.8, null);

        var result = compiler.compile("找无畏契约冠军赛主题曲", proposed);
        var corrected = compiler.withEntityCorrection(result, "VALORANT Champions", MusicEntityType.EVENT);

        assertThat(result.tool(MusicToolName.QQ_EXPANDED_SEARCH).orElseThrow().tasks())
                .extracting(MusicSearchTask::query).contains("VALORANT Champions anthem");
        assertThat(corrected.intent()).isEqualTo(MusicSearchIntent.ENTITY_RELATED);
        assertThat(corrected.tool(MusicToolName.QQ_DIRECT_SEARCH).orElseThrow().tasks().get(0).query())
                .isEqualTo("VALORANT Champions");
    }

    @Test
    void separatesSoftAvoidTermsFromHardEntityConstraints() {
        MusicSearchPlan proposed = new MusicSearchPlan(MusicSearchIntent.DISCOVERY, null,
                List.of(), null, List.of("rock"), List.of("energizing"), List.of("workout"),
                List.of(new MusicSearchTask(MusicSearchTaskType.SCENE,
                        "energizing workout rock", null, null, null)), 0.9, null);

        var result = compiler.compile("运动时听的热血摇滚，不要翻唱", proposed);

        assertThat(result.hardConstraints().track()).isNull();
        assertThat(result.softIntent().avoid()).containsExactly("翻唱");
        assertThat(result.hints().genres()).containsExactly("rock");
    }

    @Test
    void extractsLanguageAndInstrumentalHintsWithoutModelTrust() {
        MusicSearchPlan proposed = new MusicSearchPlan(MusicSearchIntent.DISCOVERY, null,
                List.of(), null, List.of("ambient"), List.of("calm"), List.of("reading"),
                List.of(), 0.9, null);

        var result = compiler.compile("阅读时听的日语纯音乐", proposed);

        assertThat(result.hints().languages()).containsExactly("Japanese", "Instrumental");
    }
}
