package com.example.agent.agent.evaluation;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.GoalConstraint;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.planner.SafeJsonPath;
import com.example.agent.skill.AgentSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GoalEvaluatorTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final GoalEvaluator evaluator = new GoalEvaluator(
            new SafeJsonPath(new ObjectMapper().findAndRegisterModules()));

    @Test
    void passesWhenEveryGoalConstraintAcceptanceAndFinalClaimAreGrounded() {
        Fixture fixture = fixture(true, 3);

        WorkflowEvaluation evaluation = evaluator.evaluate(fixture.graph(), fixture.plan(),
                fixture.evaluations(), fixture.results(), List.of(new GroundedClaim(
                        "找到了三首歌", List.of("ev-search"))));

        assertThat(evaluation.complete()).isTrue();
        assertThat(evaluation.goals()).allMatch(goal -> goal.decision() == EvaluationDecision.PASS);
    }

    @Test
    void detectsOmittedSubgoalAndUnsatisfiedQuantityConstraint() {
        Fixture fixture = fixture(false, 1);

        WorkflowEvaluation evaluation = evaluator.evaluate(fixture.graph(), fixture.plan(),
                fixture.evaluations(), fixture.results(), List.of());

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.REPLAN);
        assertThat(evaluation.goals().stream().flatMap(goal -> goal.findings().stream())
                .map(EvaluationFinding::code)).contains("GOAL_IMPLEMENTATION_OMITTED", "GOAL_CONSTRAINT_FAILED");
    }

    @Test
    void revisesFinalAnswerThatContainsAnUnsupportedConclusion() {
        Fixture fixture = fixture(true, 3);

        WorkflowEvaluation evaluation = evaluator.evaluate(fixture.graph(), fixture.plan(),
                fixture.evaluations(), fixture.results(), List.of(new GroundedClaim(
                        "这些歌一定能提升工作效率", List.of())));

        assertThat(evaluation.complete()).isFalse();
        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.REVISE);
        assertThat(evaluation.findings()).extracting(EvaluationFinding::code)
                .contains("UNGROUNDED_FINAL_CLAIM");
    }

    private Fixture fixture(boolean includeSecondGoal, int trackCount) {
        UUID graphId = UUID.randomUUID();
        GoalNode search = new GoalNode("search", "推荐三首工作音乐", GoalOperation.RECOMMEND,
                GoalTargetType.TRACK, Map.of("trackTitle", ValueExpression.literal(ValueType.STRING, "专注")),
                List.of(new GoalConstraint("count", GoalConstraint.Operator.EQUALS,
                        ValueExpression.literal(ValueType.INTEGER, 3), true, "必须正好三首")),
                List.of(), List.of(), false);
        GoalNode summary = new GoalNode("summary", "说明推荐依据", GoalOperation.SUMMARIZE,
                GoalTargetType.PROFILE, Map.of(), List.of(), List.of(), List.of(), false);
        UserGoalGraph graph = new UserGoalGraph("1.0", graphId, "推荐三首工作音乐并说明依据",
                List.of(search, summary), List.of());

        PlanTask searchTask = task("search-task", "music.track.search", "search");
        PlanTask searchAccept = task("search-accept", "planner.goal.accept", "search");
        java.util.ArrayList<PlanTask> tasks = new java.util.ArrayList<>(List.of(searchTask, searchAccept));
        if (includeSecondGoal) {
            tasks.add(task("summary-task", "profile.music.read", "summary"));
            tasks.add(task("summary-accept", "planner.goal.accept", "summary"));
        }
        CompiledPlan plan = new CompiledPlan("1.0", UUID.randomUUID(), graphId, tasks,
                tasks.stream().map(task -> List.of(task.id())).toList(), 2);

        LinkedHashMaps maps = results(tasks, trackCount);
        return new Fixture(graph, plan, maps.evaluations, maps.results);
    }

    private LinkedHashMaps results(List<PlanTask> tasks, int trackCount) {
        Map<String, TaskEvaluation> evaluations = new java.util.LinkedHashMap<>();
        Map<String, TypedTaskResult> results = new java.util.LinkedHashMap<>();
        for (PlanTask task : tasks) {
            Object output;
            String evidence;
            if (task.id().equals("search-task")) {
                output = Map.of("searchId", "s1", "tracks", java.util.stream.IntStream.range(0, trackCount)
                        .mapToObj(index -> Map.of("id", "t" + index)).toList(), "provider", "QQ_MUSIC");
                evidence = "ev-search";
            } else if (task.capabilityId().equals("planner.goal.accept")) {
                output = Map.of("accepted", true, "findings", List.of());
                evidence = "ev-accept-" + task.id();
            } else {
                output = Map.of("stage", "MATURE", "profileReady", true, "topArtists", List.of(),
                        "topTracks", List.of(), "evidenceIds", List.of("profile"));
                evidence = "ev-summary";
            }
            TypedTaskResult result = TypedTaskResult.success(task.id(),
                    registry.find(task.capabilityId()).orElseThrow().outputSchema(), output,
                    "RUNTIME", task.id().equals("search-task") ? "s1" : "", List.of(), List.of(evidence));
            results.put(task.id(), result);
            evaluations.put(task.id(), TaskEvaluation.pass(task.id()));
        }
        return new LinkedHashMaps(evaluations, results);
    }

    private static PlanTask task(String id, String capability, String goal) {
        return new PlanTask(id, id, capability, List.of(goal), Map.of(), List.of(), List.of(), 1);
    }

    private record Fixture(UserGoalGraph graph, CompiledPlan plan,
                           Map<String, TaskEvaluation> evaluations,
                           Map<String, TypedTaskResult> results) {}

    private record LinkedHashMaps(Map<String, TaskEvaluation> evaluations,
                                  Map<String, TypedTaskResult> results) {}
}
