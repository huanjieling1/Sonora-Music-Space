package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicUnderstandingBo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MusicSearchPlanGrounderTest {
    private final MusicSearchPlanGrounder grounder = new MusicSearchPlanGrounder();

    @Test
    void preservesValorantEntityAndRemovesGenericDiscoveryTasks() {
        MusicSearchPlan proposed = new MusicSearchPlan(MusicSearchIntent.DISCOVERY, null, List.of(), null,
                List.of("rock"), List.of("energizing"), List.of("gaming"),
                List.of(new MusicSearchTask(MusicSearchTaskType.GENRE, "energizing rock", null, null, null)),
                0.7, null);
        MusicUnderstandingBo understanding = understanding("VALORANT", MusicEntityType.GAME,
                List.of("无畏契约", "VALORANT"));

        MusicSearchPlan result = grounder.ground("我是说无畏契约的", proposed, understanding);

        assertThat(result.intent()).isEqualTo(MusicSearchIntent.ENTITY_RELATED);
        assertThat(result.tasks()).extracting(MusicSearchTask::type).containsOnly(MusicSearchTaskType.ENTITY);
        assertThat(result.tasks()).extracting(MusicSearchTask::query)
                .contains("无畏契约", "VALORANT official music", "VALORANT soundtrack")
                .noneMatch(query -> query.contains("Champions") || query.contains("energizing"));
    }

    @Test
    void usesChampionsOnlyWhenTheResolvedEntityIsTheEvent() {
        MusicUnderstandingBo event = understanding("VALORANT Champions", MusicEntityType.EVENT,
                List.of("无畏契约冠军赛", "VALORANT Champions"));
        MusicSearchPlan proposed = new MusicSearchPlan(MusicSearchIntent.AMBIGUOUS, null, List.of(), null,
                List.of(), List.of(), List.of(), List.of(), 0.4, "?");

        MusicSearchPlan result = grounder.ground("找无畏契约冠军赛主题曲", proposed, event);

        assertThat(result.tasks()).extracting(MusicSearchTask::query)
                .contains("无畏契约冠军赛", "VALORANT Champions anthem");
    }

    private static MusicUnderstandingBo understanding(String name, MusicEntityType type, List<String> aliases) {
        return new MusicUnderstandingBo(1L, name, type, aliases, 1, List.of("curated"),
                List.of("cinematic", "electronic"), List.of(), List.of());
    }
}
