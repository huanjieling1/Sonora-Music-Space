package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.PlanDraft;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.goal.DeterministicMusicGoalParser;
import com.example.agent.skill.AgentSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericPlanSynthesizerTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final GenericPlanSynthesizer synthesizer = new GenericPlanSynthesizer(registry);
    private final DeterministicMusicGoalParser parser = new DeterministicMusicGoalParser();

    @Test
    void synthesizesCompoundGoalsWithTypedOutputReferencesAndOneAcceptanceTaskPerGoal() {
        UserGoalGraph graph = parser.parse("找出我最喜欢的歌手资料，再推荐三首他的歌并加入队列");

        PlanDraft plan = synthesizer.synthesize(graph);

        assertThat(plan).isInstanceOf(PlanDraft.class);
        assertThat(plan.goalGraphId()).isEqualTo(graph.graphId());
        assertThat(plan.tasks()).hasSize(8);
        assertThat(plan.tasks()).extracting(PlanTask::capabilityId).containsExactly(
                "profile.artist.resolve", "planner.goal.accept",
                "qq.artist.lookup", "planner.goal.accept",
                "music.track.search", "planner.goal.accept",
                "music.queue.add", "planner.goal.accept");
        assertThat(plan.tasks().stream().filter(task -> task.capabilityId().equals("planner.goal.accept")))
                .hasSize(graph.goals().size());

        PlanTask lookup = task(plan, "task-2-execute");
        assertTaskOutput(lookup.inputs().get("artistName"), "task-1-execute", "$.artistName");
        PlanTask recommend = task(plan, "task-3-execute");
        assertTaskOutput(recommend.inputs().get("query"), "task-2-execute", "$.canonicalName");
        PlanTask queue = task(plan, "task-4-execute");
        assertTaskOutput(queue.inputs().get("tracks"), "task-3-execute", "$.tracks");
        assertThat(queue.dependencies()).contains("task-3-accept");
    }

    @Test
    void keepsParallelGoalsIndependent() {
        PlanDraft plan = synthesizer.synthesize(parser.parse(
                "搜索周杰伦的资料，同时搜索林俊杰的资料"));

        assertThat(task(plan, "task-1-execute").dependencies()).isEmpty();
        assertThat(task(plan, "task-2-execute").dependencies()).isEmpty();
    }

    @Test
    void preservesConditionalBranchOnExecutionAndAcceptanceTasks() {
        PlanDraft plan = synthesizer.synthesize(parser.parse("搜索 Mili 的歌，如果找到就播放第一首"));

        PlanTask play = task(plan, "task-2-execute");
        assertThat(play.dependencies()).containsExactly("task-1-accept");
        assertThat(play.activationConditions()).singleElement()
                .isInstanceOf(ValueExpression.Literal.class);
        assertThat(task(plan, "task-2-accept").activationConditions())
                .isEqualTo(play.activationConditions());
    }

    @Test
    void usesExplicitBindingsOrNamedUserSlotsButNeverForwardsRawRequest() {
        UserGoalGraph graph = parser.parse("推荐三首睡前歌曲");
        PlanDraft plan = synthesizer.synthesize(graph);
        PlanTask execute = task(plan, "task-1-execute");

        assertThat(execute.inputs().get("query"))
                .isEqualTo(ValueExpression.literal(com.example.agent.agent.contract.planning.ValueType.STRING,
                        "睡前"));
        assertThat(execute.inputs().get("limit"))
                .isEqualTo(ValueExpression.literal(com.example.agent.agent.contract.planning.ValueType.INTEGER, 3));
        assertThat(plan.tasks()).flatExtracting(task -> task.inputs().values())
                .noneMatch(value -> value instanceof ValueExpression.Literal literal
                        && graph.originalRequest().equals(literal.value()));

        UserGoalGraph missingQuery = new UserGoalGraph("1.0", UUID.randomUUID(), "给我一些音乐",
                List.of(goal("g", GoalOperation.RECOMMEND, GoalTargetType.TRACK)), List.of());
        ValueExpression query = task(synthesizer.synthesize(missingQuery), "task-1-execute")
                .inputs().get("query");
        assertThat(query).isEqualTo(ValueExpression.userInput(
                com.example.agent.agent.contract.planning.ValueType.STRING, "g.query", true));
    }

    @Test
    void snapshotExcludesConcreteToolImplementationNames() throws Exception {
        CapabilityRegistrySnapshot snapshot = CapabilityRegistrySnapshot.from(registry);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(snapshot);

        assertThat(json).contains("music.track.search", "supportedOperations")
                .doesNotContain("recommendMusic", "searchQqArtists", "playRecommendedTrack");
        assertThat(synthesizer.synthesize(parser.parse("推荐三首睡前歌曲"), snapshot).tasks())
                .extracting(PlanTask::capabilityId).doesNotContainAnyElementsOf(registry.toolNames());
    }

    @Test
    void rejectsPlansBeyondTaskOrDepthLimitsAndUnsupportedGoals() {
        ArrayList<GoalNode> tooMany = new ArrayList<>();
        for (int index = 0; index < 13; index++) {
            tooMany.add(goal("g" + index, GoalOperation.RECOMMEND, GoalTargetType.TRACK));
        }
        UserGoalGraph oversized = new UserGoalGraph("1.0", UUID.randomUUID(), "多个推荐目标",
                tooMany, List.of());
        assertThatThrownBy(() -> synthesizer.synthesize(oversized))
                .isInstanceOf(PlanSynthesisException.class).hasMessageContaining("任务数超过上限");

        ArrayList<GoalNode> deepGoals = new ArrayList<>();
        ArrayList<GoalRelation> deepRelations = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            deepGoals.add(goal("deep-" + index, GoalOperation.RECOMMEND, GoalTargetType.TRACK));
            if (index > 0) deepRelations.add(new GoalRelation("deep-" + (index - 1), "deep-" + index,
                    GoalRelation.Type.SEQUENCE, null, "串行"));
        }
        UserGoalGraph tooDeep = new UserGoalGraph("1.0", UUID.randomUUID(), "连续七个推荐目标",
                deepGoals, deepRelations);
        assertThatThrownBy(() -> synthesizer.synthesize(tooDeep))
                .isInstanceOf(PlanSynthesisException.class).hasMessageContaining("深度超过上限");

        UserGoalGraph unsupported = new UserGoalGraph("1.0", UUID.randomUUID(), "删除专辑",
                List.of(goal("delete", GoalOperation.DELETE, GoalTargetType.ALBUM)), List.of());
        assertThatThrownBy(() -> synthesizer.synthesize(unsupported))
                .isInstanceOf(PlanSynthesisException.class).hasMessageContaining("没有已注册能力");
    }

    private static GoalNode goal(String id, GoalOperation operation, GoalTargetType target) {
        return new GoalNode(id, id, operation, target, Map.of(), List.of(), List.of(), List.of(), false);
    }

    private static PlanTask task(PlanDraft plan, String id) {
        return plan.tasks().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private static void assertTaskOutput(ValueExpression expression, String taskId, String path) {
        assertThat(expression).isInstanceOf(ValueExpression.TaskOutput.class);
        ValueExpression.TaskOutput output = (ValueExpression.TaskOutput) expression;
        assertThat(output.taskId()).isEqualTo(taskId);
        assertThat(output.path()).isEqualTo(path);
    }
}
