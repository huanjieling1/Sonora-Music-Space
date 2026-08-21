package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.skill.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanSchemaPropertyTest {
    private static final long SEED = 20260821L;
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final GenericPlanSynthesizer synthesizer = new GenericPlanSynthesizer(registry);
    private final PlanCompiler compiler = new PlanCompiler(new PlanValidator(registry));

    @Test
    void generatedAcyclicGoalGraphsAlwaysCompileToBoundedTopologicalPlans() {
        Random random = new Random(SEED);
        for (int sample = 0; sample < 128; sample++) {
            int size = 1 + random.nextInt(6);
            UserGoalGraph graph = graph("property-request-" + sample, size, random, false);

            CompiledPlan plan = compiler.compile(graph, synthesizer.synthesize(graph),
                    PlanValidationContext.standard("property-user"));

            assertThat(plan.tasks()).hasSize(size * 2);
            assertThat(plan.tasks()).hasSizeLessThanOrEqualTo(GenericPlanSynthesizer.MAX_TASKS);
            Map<String, Integer> stages = stageIndexes(plan);
            plan.tasks().forEach(task -> task.dependencies().forEach(dependency ->
                    assertThat(stages.get(dependency)).isLessThan(stages.get(task.id()))));
        }
    }

    @Test
    void generatedCyclesAreAlwaysRejectedBeforeCompilation() {
        Random random = new Random(SEED);
        for (int size = 2; size <= 6; size++) {
            for (int sample = 0; sample < 16; sample++) {
                UserGoalGraph graph = graph("cycle-request-" + size + "-" + sample,
                        size, random, true);
                assertThatThrownBy(() -> synthesizer.synthesize(graph))
                        .isInstanceOf(PlanSynthesisException.class)
                        .hasMessageContaining("循环依赖");
            }
        }
    }

    @Test
    void arbitraryRawRequestMarkersAreNeverForwardedAsCapabilityArguments() {
        for (int sample = 0; sample < 64; sample++) {
            String marker = "RAW-REQUEST-SECRET-" + sample + "-" + UUID.randomUUID();
            UserGoalGraph safe = graph(marker, 1, new Random(SEED + sample), false);
            assertThat(synthesizer.synthesize(safe).tasks()).allSatisfy(task ->
                    assertThat(task.inputs().values()).noneMatch(value -> containsLiteral(value, marker)));

            GoalNode poisoned = goal("goal-1", marker);
            UserGoalGraph attack = new UserGoalGraph("1.0", UUID.randomUUID(), marker,
                    List.of(new GoalNode(poisoned.id(), poisoned.title(), poisoned.operation(),
                            poisoned.targetType(), Map.of("trackTitle",
                            ValueExpression.literal(ValueType.STRING, marker)), poisoned.constraints(),
                            poisoned.acceptanceCriteria(), poisoned.missingSlots(), false)), List.of());
            assertThatThrownBy(() -> synthesizer.synthesize(attack))
                    .isInstanceOf(PlanSynthesisException.class)
                    .hasMessageContaining("原始请求");
        }
    }

    private static UserGoalGraph graph(String request, int size, Random random, boolean cycle) {
        ArrayList<GoalNode> goals = new ArrayList<>();
        for (int index = 0; index < size; index++) goals.add(goal("goal-" + (index + 1), "track-" + index));
        ArrayList<GoalRelation> relations = new ArrayList<>();
        for (int index = 1; index < size; index++) {
            GoalRelation.Type type = cycle ? GoalRelation.Type.SEQUENCE
                    : random.nextBoolean() ? GoalRelation.Type.SEQUENCE
                    : GoalRelation.Type.PARALLEL;
            relations.add(new GoalRelation(goals.get(index - 1).id(), goals.get(index).id(),
                    type, null, "generated DAG edge"));
        }
        if (cycle) {
            relations.removeIf(value -> value.sourceGoalId().equals(goals.get(size - 1).id())
                    && value.targetGoalId().equals(goals.get(0).id()));
            relations.add(new GoalRelation(goals.get(size - 1).id(), goals.get(0).id(),
                    GoalRelation.Type.DEPENDS_ON, null, "generated cycle edge"));
        }
        return new UserGoalGraph("1.0", UUID.randomUUID(), request, goals, relations);
    }

    private static GoalNode goal(String id, String title) {
        return new GoalNode(id, "搜索歌曲 " + title, GoalOperation.SEARCH, GoalTargetType.TRACK,
                Map.of("trackTitle", ValueExpression.literal(ValueType.STRING, title)), List.of(),
                List.of(new AcceptanceCriterion(id + "-output", AcceptanceCriterion.Type.OUTPUT_PRESENT,
                        "$.tracks", null, true, "必须返回歌曲", Map.of())), List.of(), false);
    }

    private static Map<String, Integer> stageIndexes(CompiledPlan plan) {
        HashMap<String, Integer> result = new HashMap<>();
        for (int stage = 0; stage < plan.executionStages().size(); stage++) {
            for (String task : plan.executionStages().get(stage)) result.put(task, stage);
        }
        return result;
    }

    private static boolean containsLiteral(ValueExpression expression, String marker) {
        return expression instanceof ValueExpression.Literal literal && marker.equals(literal.value());
    }
}
