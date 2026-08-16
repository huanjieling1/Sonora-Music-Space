package com.example.agent.service.impl;

import com.example.agent.config.AgentProperties;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchTaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlmMusicQueryPlannerTest {
    private final MusicTaxonomyService taxonomy = new MusicTaxonomyService();
    private final GlmMusicQueryPlanner planner = new GlmMusicQueryPlanner(propertiesWithoutModel(), taxonomy);

    @Test
    void identifiesExactTrackAndArtist() {
        var plan = planner.plan("播放 Alan Walker 的 Faded");

        assertThat(plan.intent()).isEqualTo(MusicSearchIntent.EXACT_TRACK);
        assertThat(plan.track()).isEqualTo("Faded");
        assertThat(plan.artists()).containsExactly("Alan Walker");
        assertThat(plan.tasks()).first().satisfies(task -> {
            assertThat(task.type()).isEqualTo(MusicSearchTaskType.TRACK_ARTIST);
            assertThat(task.query()).isEqualTo("Faded Alan Walker");
        });
    }

    @Test
    void distinguishesArtistAndAlbumRequests() {
        var artist = planner.plan("听周杰伦的歌");
        var album = planner.plan("播放周杰伦的专辑《叶惠美》");

        assertThat(artist.intent()).isEqualTo(MusicSearchIntent.ARTIST);
        assertThat(artist.artists()).containsExactly("周杰伦");
        assertThat(album.intent()).isEqualTo(MusicSearchIntent.ALBUM);
        assertThat(album.album()).isEqualTo("叶惠美");
        assertThat(album.artists()).containsExactly("周杰伦");
    }

    @Test
    void translatesGenreMoodAndSceneIntoSeveralDiscoveryRoutes() {
        var plan = planner.plan("深夜写代码，安静、有一点未来感的电子乐");

        assertThat(plan.intent()).isEqualTo(MusicSearchIntent.DISCOVERY);
        assertThat(plan.genres()).contains("electronic");
        assertThat(plan.moods()).contains("calm", "futuristic");
        assertThat(plan.scenes()).contains("coding", "late night");
        assertThat(plan.tasks()).extracting(task -> task.query())
                .contains("electronic calm", "coding electronic calm");
    }

    @Test
    void marksBareEntityAsAmbiguousInsteadOfInventingItsType() {
        var plan = planner.plan("Faded");

        assertThat(plan.intent()).isEqualTo(MusicSearchIntent.AMBIGUOUS);
        assertThat(plan.confidence()).isLessThan(0.5);
        assertThat(plan.clarificationQuestion()).contains("歌曲名", "歌手名", "专辑名");
    }

    private static AgentProperties propertiesWithoutModel() {
        return new AgentProperties("", "https://model.test", "test-model", 0, 512,
                20, 5, false, false);
    }
}
