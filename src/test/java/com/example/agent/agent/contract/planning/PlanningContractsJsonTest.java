package com.example.agent.agent.contract.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningContractsJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void goalGraphRoundTripsAResolveLookupRecommendAndQueueRequest() throws Exception {
        UserGoalGraph graph = goalGraph();

        String json = objectMapper.writeValueAsString(graph);
        UserGoalGraph restored = objectMapper.readValue(json, UserGoalGraph.class);

        assertThat(restored).isEqualTo(graph);
        assertThat(restored.goals()).extracting(GoalNode::operation)
                .containsExactly(GoalOperation.RESOLVE, GoalOperation.LOOKUP,
                        GoalOperation.RECOMMEND, GoalOperation.QUEUE_ADD);
        assertThat(restored.relations()).extracting(GoalRelation::sourceGoalId, GoalRelation::targetGoalId)
                .containsExactly(tuple("favorite-artist", "artist-profile"),
                        tuple("favorite-artist", "artist-tracks"),
                        tuple("artist-tracks", "queue-tracks"));
        assertThat(json).contains("\"kind\":\"PROFILE_VALUE\"", "\"kind\":\"LITERAL\"");
    }

    @Test
    void planDraftAndCompiledPlanRoundTripTypedTaskReferences() throws Exception {
        UserGoalGraph graph = goalGraph();
        List<PlanTask> tasks = tasks();
        PlanDraft draft = new PlanDraft("1.0", UUID.randomUUID(), graph.graphId(), tasks, 2);
        CompiledPlan compiled = new CompiledPlan("1.0", draft.planId(), graph.graphId(), tasks,
                List.of(List.of("resolve-favorite-artist"),
                        List.of("lookup-artist", "recommend-tracks"),
                        List.of("queue-tracks")), 2);

        String draftJson = objectMapper.writeValueAsString(draft);
        PlanDraft restoredDraft = objectMapper.readValue(draftJson, PlanDraft.class);
        String compiledJson = objectMapper.writeValueAsString(compiled);
        CompiledPlan restoredCompiled = objectMapper.readValue(compiledJson, CompiledPlan.class);

        assertThat(restoredDraft).isEqualTo(draft);
        assertThat(restoredCompiled).isEqualTo(compiled);
        assertThat(restoredDraft.tasks().get(1).inputs().get("artistName"))
                .isInstanceOf(ValueExpression.TaskOutput.class)
                .extracting(ValueExpression::kind).isEqualTo(ValueExpression.Kind.TASK_OUTPUT);
        assertThat(draftJson).contains("\"kind\":\"TASK_OUTPUT\"");
    }

    @Test
    void contractsDefensivelyCopyMutableInputs() {
        ArrayList<GoalNode> goals = new ArrayList<>(goalGraph().goals());
        UserGoalGraph graph = new UserGoalGraph("1.0", UUID.randomUUID(), "测试请求", goals, List.of());
        LinkedHashMap<String, Object> literalObject = new LinkedHashMap<>();
        literalObject.put("limit", 3);
        ValueExpression.Literal literal = ValueExpression.literal(ValueType.OBJECT, literalObject);

        goals.clear();
        literalObject.put("unexpected", true);

        assertThat(graph.goals()).hasSize(4);
        assertThat(((Map<?, ?>) literal.value()).size()).isEqualTo(1);
        assertThat(((Map<?, ?>) literal.value()).get("limit")).isEqualTo(3);
        assertThatThrownBy(() -> graph.goals().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsStructurallyInvalidContractValuesEarly() {
        assertThatThrownBy(() -> new GoalRelation("same", "same", GoalRelation.Type.DEPENDS_ON,
                null, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("依赖自身");
        assertThatThrownBy(() -> new GoalRelation("a", "b", GoalRelation.Type.CONDITIONAL,
                null, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("condition");
        assertThatThrownBy(() -> new PlanTask("task", "任务", "capability", List.of("goal"),
                Map.of(), List.of(), List.of(), 4))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1 到 3");
    }

    private static UserGoalGraph goalGraph() {
        UUID graphId = UUID.randomUUID();
        GoalNode resolve = new GoalNode("favorite-artist", "确定最喜欢的歌手",
                GoalOperation.RESOLVE, GoalTargetType.ARTIST,
                Map.of("profile", ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile")),
                List.of(), List.of(criterion("artist-resolved", AcceptanceCriterion.Type.OUTPUT_PRESENT,
                "$.artistName", null)), List.of(), false);
        GoalNode profile = new GoalNode("artist-profile", "查询歌手资料",
                GoalOperation.LOOKUP, GoalTargetType.ARTIST, Map.of(), List.of(),
                List.of(criterion("profile-source", AcceptanceCriterion.Type.SOURCE, "$.provider",
                        ValueExpression.literal(ValueType.STRING, "QQ_MUSIC"))), List.of(), false);
        GoalNode tracks = new GoalNode("artist-tracks", "推荐三首歌手作品",
                GoalOperation.RECOMMEND, GoalTargetType.TRACK,
                Map.of("limit", ValueExpression.literal(ValueType.INTEGER, 3)),
                List.of(new GoalConstraint("count", GoalConstraint.Operator.EQUALS,
                        ValueExpression.literal(ValueType.INTEGER, 3), true, "必须推荐三首")),
                List.of(criterion("track-count", AcceptanceCriterion.Type.COUNT, "$.tracks",
                        ValueExpression.literal(ValueType.INTEGER, 3))), List.of(), false);
        GoalNode queue = new GoalNode("queue-tracks", "加入播放队列",
                GoalOperation.QUEUE_ADD, GoalTargetType.QUEUE, Map.of(), List.of(),
                List.of(criterion("queue-updated", AcceptanceCriterion.Type.STATE_CHANGE,
                        "$.queue", ValueExpression.literal(ValueType.BOOLEAN, true))), List.of(), true);
        return new UserGoalGraph("1.0", graphId,
                "找出我最喜欢的歌手资料，再推荐三首他的歌并加入队列",
                List.of(resolve, profile, tracks, queue),
                List.of(new GoalRelation("favorite-artist", "artist-profile",
                                GoalRelation.Type.DEPENDS_ON, null, "资料查询依赖歌手实体"),
                        new GoalRelation("favorite-artist", "artist-tracks",
                                GoalRelation.Type.DEPENDS_ON, null, "推荐依赖歌手实体"),
                        new GoalRelation("artist-tracks", "queue-tracks",
                                GoalRelation.Type.SEQUENCE, null, "先得到歌曲再加入队列")));
    }

    private static List<PlanTask> tasks() {
        AcceptanceCriterion artist = criterion("artist-resolved", AcceptanceCriterion.Type.OUTPUT_PRESENT,
                "$.artistName", null);
        AcceptanceCriterion profile = criterion("profile-source", AcceptanceCriterion.Type.SOURCE,
                "$.provider", ValueExpression.literal(ValueType.STRING, "QQ_MUSIC"));
        AcceptanceCriterion count = criterion("track-count", AcceptanceCriterion.Type.COUNT,
                "$.tracks", ValueExpression.literal(ValueType.INTEGER, 3));
        AcceptanceCriterion queue = criterion("queue-updated", AcceptanceCriterion.Type.STATE_CHANGE,
                "$.queue", ValueExpression.literal(ValueType.BOOLEAN, true));
        return List.of(
                new PlanTask("resolve-favorite-artist", "从画像解析歌手", "profile.artist.resolve",
                        List.of("favorite-artist"),
                        Map.of("profile", ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile")),
                        List.of(), List.of(artist), 1),
                new PlanTask("lookup-artist", "查询歌手资料", "qq.artist.lookup",
                        List.of("artist-profile"),
                        Map.of("artistName", ValueExpression.taskOutput(ValueType.STRING,
                                "resolve-favorite-artist", "$.artistName")),
                        List.of("resolve-favorite-artist"), List.of(profile), 2),
                new PlanTask("recommend-tracks", "推荐歌手作品", "music.track.recommend",
                        List.of("artist-tracks"),
                        Map.of("artistName", ValueExpression.taskOutput(ValueType.STRING,
                                        "resolve-favorite-artist", "$.artistName"),
                                "limit", ValueExpression.literal(ValueType.INTEGER, 3)),
                        List.of("resolve-favorite-artist"), List.of(count), 2),
                new PlanTask("queue-tracks", "歌曲加入队列", "music.queue.add",
                        List.of("queue-tracks"),
                        Map.of("tracks", ValueExpression.taskOutput(ValueType.ARRAY,
                                "recommend-tracks", "$.tracks")),
                        List.of("recommend-tracks"), List.of(queue), 1));
    }

    private static AcceptanceCriterion criterion(String id, AcceptanceCriterion.Type type,
                                                   String subject, ValueExpression expected) {
        return new AcceptanceCriterion(id, type, subject, expected, true, "", Map.of());
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }
}
