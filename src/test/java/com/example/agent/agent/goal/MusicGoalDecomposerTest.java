package com.example.agent.agent.goal;

import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicGoalDecomposerTest {
    private final DeterministicMusicGoalParser parser = new DeterministicMusicGoalParser();
    private final MusicGoalGraphCorrector corrector = new MusicGoalGraphCorrector();
    private final MusicGoalCompatibilityAdapter compatibility = new MusicGoalCompatibilityAdapter();

    @Test
    void deterministicCorpusPreservesGoalsTargetsAndRelations() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream input = getClass().getResourceAsStream("/goal-decomposition-corpus.json")) {
            List<CorpusCase> cases = mapper.readValue(input, new TypeReference<>() {});
            assertThat(cases).isNotEmpty();
            for (CorpusCase item : cases) {
                UserGoalGraph graph = parser.parse(item.request());
                assertThat(graph.originalRequest()).as(item.request()).isEqualTo(item.request());
                assertThat(graph.goals()).as(item.request()).extracting(GoalNode::operation)
                        .containsExactlyElementsOf(item.operations());
                assertThat(graph.goals()).as(item.request()).extracting(GoalNode::targetType)
                        .containsExactlyElementsOf(item.targets());
                assertThat(graph.relations()).as(item.request()).extracting(GoalRelation::type)
                        .containsExactlyInAnyOrderElementsOf(item.relationTypes());
            }
        }
    }

    @Test
    void preservesExplicitArtistTrackCountSceneAndPosition() {
        UserGoalGraph graph = parser.parse("搜索周杰伦的《晴天》，再推荐三首跑步歌曲，然后播放第一首");

        GoalNode search = graph.goals().get(0);
        GoalNode recommend = graph.goals().get(1);
        GoalNode play = graph.goals().get(2);
        assertLiteral(search, "artistName", "周杰伦");
        assertLiteral(search, "trackTitle", "晴天");
        assertLiteral(recommend, "limit", 3);
        assertThat(recommend.constraints()).anyMatch(value -> "scene".equals(value.field())
                && value.expected() instanceof ValueExpression.Literal literal
                && "跑步".equals(literal.value()));
        assertLiteral(play, "position", 1);
    }

    @Test
    void bindsPronounToPreviousArtistAndMarksStateOperationsForConfirmation() {
        UserGoalGraph graph = parser.parse("找出我最喜欢的歌手资料，同时推荐三首他的歌并加入队列");

        GoalNode resolve = graph.goals().get(0);
        GoalNode recommend = graph.goals().get(2);
        GoalNode queue = graph.goals().get(3);
        assertThat(graph.relations()).anyMatch(value -> value.sourceGoalId().equals(resolve.id())
                && value.targetGoalId().equals(recommend.id())
                && value.type() == GoalRelation.Type.DEPENDS_ON);
        assertThat(queue.requiresConfirmation()).isTrue();
        assertLiteral(recommend, "reference", "他");
    }

    @Test
    void asksForMissingArtistInsteadOfInventingOne() {
        UserGoalGraph graph = parser.parse("查询歌手资料");

        assertThat(graph.goals()).singleElement().satisfies(goal -> {
            assertThat(goal.targetType()).isEqualTo(GoalTargetType.ARTIST);
            assertThat(goal.missingSlots()).contains("artistName");
            assertThat(goal.inputs()).doesNotContainKey("artistName");
        });
    }

    @Test
    void correctorRejectsInventedEntityAndRestoresExplicitSlots() {
        String request = "介绍歌手 Mili 的资料";
        UserGoalGraph fallback = parser.parse(request);
        GoalNode invented = new GoalNode("model-goal", "Taylor Swift 的资料", GoalOperation.LOOKUP,
                GoalTargetType.ARTIST,
                Map.of("artistName", ValueExpression.literal(ValueType.STRING, "Taylor Swift")),
                List.of(), List.of(criterion("model-output")), List.of(), false);
        UserGoalGraph model = new UserGoalGraph("1.0", UUID.randomUUID(), "changed",
                List.of(invented), List.of());

        UserGoalGraph corrected = corrector.correct(request, model, fallback);

        assertThat(corrected.originalRequest()).isEqualTo(request);
        assertLiteral(corrected.goals().get(0), "artistName", "Mili");
        assertThat(corrected.goals().get(0).title()).doesNotContain("Taylor Swift");
    }

    @Test
    void decomposerFallsBackWhenLanguageAgentIsUnavailable() {
        MusicGoalDecomposer decomposer = new MusicGoalDecomposer(value -> Optional.empty(), parser,
                corrector, compatibility);

        UserGoalGraph graph = decomposer.decompose("推荐三首睡前歌曲并加入队列");

        assertThat(graph.goals()).extracting(GoalNode::operation)
                .containsExactly(GoalOperation.RECOMMEND, GoalOperation.QUEUE_ADD);
    }

    @Test
    void correctorKeepsGroundedAdditionalModelIntentWhenDeterministicFallbackMissesIt() {
        String request = "推荐 Mili 的歌，同时查询她的资料";
        UserGoalGraph fallback = parser.parse("推荐 Mili 的歌");
        GoalNode recommend = fallback.goals().get(0);
        GoalNode lookup = new GoalNode("artist-lookup", "查询 Mili 的资料", GoalOperation.LOOKUP,
                GoalTargetType.ARTIST,
                Map.of("artistName", ValueExpression.literal(ValueType.STRING, "Mili")),
                List.of(), List.of(criterion("artist-output")), List.of(), false);
        UserGoalGraph model = new UserGoalGraph("1.0", UUID.randomUUID(), request,
                List.of(recommend, lookup), List.of(new GoalRelation(recommend.id(), lookup.id(),
                GoalRelation.Type.PARALLEL, null, "同时执行")));

        UserGoalGraph corrected = corrector.correct(request, model, fallback);

        assertThat(corrected.goals()).extracting(GoalNode::operation)
                .containsExactly(GoalOperation.RECOMMEND, GoalOperation.LOOKUP);
        assertLiteral(corrected.goals().get(1), "artistName", "Mili");
        assertThat(corrected.relations()).extracting(GoalRelation::type)
                .contains(GoalRelation.Type.PARALLEL);
    }

    @Test
    void singleGoalCompatibilityRoundTripsLegacyIntent() {
        MusicIntentDraft legacy = new MusicIntentDraft(MusicIntentDraft.Action.RECOMMEND,
                MusicIntentDraft.Target.TRACK, MusicIntentDraft.Mode.DISCOVERY,
                MusicIntentDraft.RankingMetric.NONE, MusicIntentDraft.TimeWindow.UNSPECIFIED,
                List.of("睡前"), true, List.of(), 0.9, MusicIntentDraft.Domain.MUSIC);

        UserGoalGraph graph = compatibility.fromIntent("推荐适合睡前的歌曲", legacy);
        MusicIntentDraft restored = compatibility.toIntent(graph).orElseThrow();

        assertThat(graph.goals()).hasSize(1);
        assertThat(restored.action()).isEqualTo(MusicIntentDraft.Action.RECOMMEND);
        assertThat(restored.target()).isEqualTo(MusicIntentDraft.Target.TRACK);
        assertThat(restored.scenes()).containsExactly("睡前");
        assertThat(restored.personalized()).isTrue();
    }

    @Test
    void multiGoalGraphCannotBeProjectedIntoOneLegacyIntent() {
        assertThat(compatibility.toIntent(parser.parse("推荐歌曲并加入队列"))).isEmpty();
    }

    private static void assertLiteral(GoalNode goal, String key, Object expected) {
        assertThat(goal.inputs().get(key)).isInstanceOf(ValueExpression.Literal.class);
        assertThat(((ValueExpression.Literal) goal.inputs().get(key)).value()).isEqualTo(expected);
    }

    private static AcceptanceCriterion criterion(String id) {
        return new AcceptanceCriterion(id, AcceptanceCriterion.Type.OUTPUT_PRESENT,
                "$.result", null, true, "", Map.of());
    }

    private record CorpusCase(String request, List<GoalOperation> operations,
                              List<GoalTargetType> targets, List<GoalRelation.Type> relationTypes) {}
}
