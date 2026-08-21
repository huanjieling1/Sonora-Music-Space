package com.example.agent.orchestration.migration;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.AgentScopeDecision;
import com.example.agent.agent.capability.AgentScopeType;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.skill.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyRouteGoalGraphAdapterTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final GenericPlanSynthesizer planner = new GenericPlanSynthesizer(registry);
    private final LegacyRouteGoalGraphAdapter adapter = new LegacyRouteGoalGraphAdapter(
            new com.example.agent.agent.goal.MusicGoalDecomposer());

    @Test
    void migratesEveryPhaseTwelveFeatureToRegisteredCapabilities() {
        assertCapabilities(MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE,
                "把你认为我最喜欢的歌手资料找出来", draft(MusicIntentDraft.Action.SEARCH,
                        MusicIntentDraft.Target.ARTIST, false), MusicTurnPlan.none(),
                "profile.artist.resolve", "qq.artist.lookup");
        assertCapabilities(MusicAgentRoute.MUSIC_DISCOVERY, "搜索歌手 Mili 的歌曲",
                draft(MusicIntentDraft.Action.SEARCH, MusicIntentDraft.Target.TRACK, false),
                MusicTurnPlan.none(), "music.track.search");
        assertCapabilities(MusicAgentRoute.ARTIST_LOOKUP, "介绍歌手 Mili 的资料",
                draft(MusicIntentDraft.Action.SEARCH, MusicIntentDraft.Target.ARTIST, false),
                MusicTurnPlan.none(), "qq.artist.lookup");
        assertCapabilities(MusicAgentRoute.PLAYLIST_SEARCH, "搜索无畏契约歌单",
                draft(MusicIntentDraft.Action.SEARCH, MusicIntentDraft.Target.PLAYLIST, false),
                MusicTurnPlan.none(), "qq.playlist.search");
        assertCapabilities(MusicAgentRoute.QQ_TREND_DISCOVERY, "查看本周热门榜单",
                draft(MusicIntentDraft.Action.SEARCH, MusicIntentDraft.Target.CHART, false),
                MusicTurnPlan.none(), "qq.chart.read");
        assertCapabilities(MusicAgentRoute.MUSIC_DISCOVERY, "推荐三首工作音乐",
                draft(MusicIntentDraft.Action.RECOMMEND, MusicIntentDraft.Target.TRACK, true),
                MusicTurnPlan.none(), "music.track.search");
        assertCapabilities(MusicAgentRoute.RESULT_PLAYBACK, "播放第二首",
                draft(MusicIntentDraft.Action.PLAY, MusicIntentDraft.Target.SEARCH_RESULT, false),
                MusicTurnPlan.none(), "music.playback.play");
        assertCapabilities(MusicAgentRoute.QUEUE_CONTROL, "把这些歌全部加入队列",
                draft(MusicIntentDraft.Action.QUEUE, MusicIntentDraft.Target.QUEUE, false),
                MusicTurnPlan.none(), "music.queue.add");

        MusicTurnPlan feedback = new MusicTurnPlan(true, true, true,
                List.of(new MusicPreferenceChange(MusicPreferenceType.GENRE, "摇滚", 1, true)),
                true, "推荐摇滚歌曲", true, 0.95, "");
        assertCapabilities(MusicAgentRoute.RECOMMENDATION_FOLLOW_UP, "这批不喜欢，换成摇滚",
                draft(MusicIntentDraft.Action.RECOMMEND, MusicIntentDraft.Target.TRACK, true), feedback,
                "music.recommendation.feedback", "music.track.search");
    }

    @Test
    void personalizedRecommendationCarriesProfileAsDeclaredInput() {
        var graph = graph(MusicAgentRoute.MUSIC_DISCOVERY, "推荐三首工作音乐",
                draft(MusicIntentDraft.Action.RECOMMEND, MusicIntentDraft.Target.TRACK, true),
                MusicTurnPlan.none());

        assertThat(graph.goals()).singleElement().satisfies(goal ->
                assertThat(goal.inputs()).containsKey("profile"));
    }

    private void assertCapabilities(MusicAgentRoute route, String request, MusicIntentDraft intent,
                                    MusicTurnPlan followUp, String... expected) {
        var plan = planner.synthesize(graph(route, request, intent, followUp));
        assertThat(plan.tasks().stream().map(task -> task.capabilityId())
                .filter(id -> !"planner.goal.accept".equals(id)).toList())
                .containsExactly(expected);
    }

    private com.example.agent.agent.contract.planning.UserGoalGraph graph(
            MusicAgentRoute route, String request, MusicIntentDraft intent, MusicTurnPlan followUp) {
        MusicAgentTurn turn = new MusicAgentTurn(1, UUID.randomUUID(), request);
        return adapter.adapt(turn, understanding(route, intent, followUp)).orElseThrow();
    }

    private static MusicGoalUnderstanding understanding(MusicAgentRoute route, MusicIntentDraft intent,
                                                        MusicTurnPlan followUp) {
        return new MusicGoalUnderstanding(MusicIntentUnderstanding.routed(route, intent), route,
                new AgentScopeDecision(AgentScopeType.MUSIC, "test"), intent.personalized(), false,
                followUp, MusicSupportContext.none(), null, "test", List.of());
    }

    private static MusicIntentDraft draft(MusicIntentDraft.Action action, MusicIntentDraft.Target target,
                                          boolean personalized) {
        return new MusicIntentDraft(action, target, MusicIntentDraft.Mode.EXACT,
                MusicIntentDraft.RankingMetric.NONE, MusicIntentDraft.TimeWindow.UNSPECIFIED,
                List.of(), personalized, List.of(), 1, MusicIntentDraft.Domain.MUSIC);
    }
}
