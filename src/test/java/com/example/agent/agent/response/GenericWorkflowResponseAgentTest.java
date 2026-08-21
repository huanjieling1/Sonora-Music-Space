package com.example.agent.agent.response;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySchema;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedEntityReference;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.evaluation.EvaluationDecision;
import com.example.agent.agent.evaluation.EvaluationFinding;
import com.example.agent.agent.evaluation.TaskEvaluation;
import com.example.agent.orchestration.dag.DagExecutionSnapshot;
import com.example.agent.orchestration.dag.DagTaskState;
import com.example.agent.orchestration.dag.DagTaskStatus;
import com.example.agent.orchestration.dag.DagWorkflowStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericWorkflowResponseAgentTest {
    private static final CapabilitySchema OUTPUT = CapabilitySchema.empty("response-test-output.v1");

    @Test
    void guardsArbitraryGoalsInOriginalOrderAndExcludesUngroundedResults() {
        Fixture fixture = fixture(false);

        GenericWorkflowResponse response = agent().respond(fixture.graph(), fixture.snapshot());

        assertTrue(response.guarded());
        assertEquals(List.of("favorite", "search", "partial", "failed"),
                response.goals().stream().map(GoalResponseSection::goalId).toList());
        assertEquals(List.of(GoalResponseStatus.COMPLETED, GoalResponseStatus.COMPLETED,
                        GoalResponseStatus.PARTIAL, GoalResponseStatus.FAILED),
                response.goals().stream().map(GoalResponseSection::status).toList());
        assertEquals(GroundedResponseFact.Kind.INFERENCE,
                response.goals().get(0).facts().get(0).kind());
        assertEquals(GroundedResponseFact.Kind.EXTERNAL_FACT,
                response.goals().get(1).facts().get(0).kind());
        assertEquals(3, response.claims().size());
        assertTrue(response.claims().stream().allMatch(claim -> !claim.evidenceIds().isEmpty()));

        assertEquals(List.of("favorite", "search"),
                response.actions().stream().map(ResponseCardAction::goalId).toList());
        assertEquals(Map.of("stage", "stable"), response.actions().get(0).payload());
        assertEquals(Map.of("itemCount", 1), response.actions().get(1).payload());
        assertEquals("Mili", response.actions().get(0).entities().get(0).canonicalName());

        assertTrue(response.answer().contains("[推断]"));
        assertTrue(response.answer().contains("[外部事实]"));
        assertTrue(response.answer().contains("[部分完成]"));
        assertTrue(response.answer().contains("[未完成]"));
        assertTrue(response.answer().indexOf("确定偏好歌手") < response.answer().indexOf("搜索歌曲"));
        assertFalse(response.answer().contains("UNVERIFIED_ARTIST"));
        assertFalse(response.answer().contains("UNVERIFIED_TRACK"));
        assertFalse(response.answer().contains("FAILED_RESULT_LEAK"));
        assertFalse(response.actions().toString().contains("UNVERIFIED"));
        assertFalse(response.actions().toString().contains("FAILED_RESULT_LEAK"));
    }

    @Test
    void graphMismatchCannotPublishFactsEntitiesOrActions() {
        Fixture fixture = fixture(true);

        GenericWorkflowResponse response = agent().respond(fixture.graph(), fixture.snapshot());

        assertTrue(response.goals().stream().allMatch(goal -> goal.status() == GoalResponseStatus.FAILED));
        assertTrue(response.goals().stream().allMatch(goal -> goal.facts().isEmpty()));
        assertTrue(response.actions().isEmpty());
        assertTrue(response.claims().isEmpty());
    }

    private static GenericWorkflowResponseAgent agent() {
        ResponseArtifactFactory artifacts = new ResponseArtifactFactory(new AgentCapabilityRegistry());
        return new GenericWorkflowResponseAgent(new FinalResponseGuard(artifacts));
    }

    private static Fixture fixture(boolean mismatchedGraph) {
        UUID graphId = UUID.randomUUID();
        List<GoalNode> goals = List.of(
                goal("favorite", "确定偏好歌手", GoalOperation.RESOLVE, GoalTargetType.ARTIST),
                goal("search", "搜索歌曲", GoalOperation.SEARCH, GoalTargetType.TRACK),
                goal("partial", "补充推荐歌曲", GoalOperation.RECOMMEND, GoalTargetType.TRACK),
                goal("failed", "查询失败分支", GoalOperation.SEARCH, GoalTargetType.TRACK));
        UserGoalGraph graph = new UserGoalGraph("1.0", graphId, "确定偏好歌手并搜索、推荐歌曲",
                goals, List.of());

        ArrayList<PlanTask> tasks = new ArrayList<>();
        ArrayList<DagTaskState> states = new ArrayList<>();
        addCompleted(tasks, states, goals.get(0), "profile.artist.resolve",
                Map.of("artistName", "UNVERIFIED_ARTIST", "stage", "stable"),
                entity(GoalTargetType.ARTIST, "Mili", "artist-1"), true);
        addCompleted(tasks, states, goals.get(1), "music.track.search",
                Map.of("tracks", List.of(Map.of("name", "UNVERIFIED_TRACK"))),
                entity(GoalTargetType.TRACK, "world.execute(me);", "track-1"), true);
        addCompleted(tasks, states, goals.get(2), "music.track.search",
                Map.of("tracks", List.of(Map.of("name", "UNVERIFIED_PARTIAL"))),
                entity(GoalTargetType.TRACK, "Ga1ahad and Scientific Witchery", "track-2"), false);
        addFailed(tasks, states, goals.get(3));

        UUID planGraphId = mismatchedGraph ? UUID.randomUUID() : graphId;
        CompiledPlan plan = new CompiledPlan("1.0", UUID.randomUUID(), planGraphId, tasks,
                tasks.stream().map(task -> List.of(task.id())).toList(), 1);
        Instant now = Instant.now();
        DagExecutionSnapshot snapshot = new DagExecutionSnapshot(UUID.randomUUID(), "user-1", "chat-1",
                plan, DagWorkflowStatus.FAILED, states, Map.of(), now, now);
        return new Fixture(graph, snapshot);
    }

    private static GoalNode goal(String id, String title, GoalOperation operation, GoalTargetType target) {
        return new GoalNode(id, title, operation, target, Map.of(), List.of(), List.of(), List.of(), false);
    }

    private static void addCompleted(List<PlanTask> tasks, List<DagTaskState> states, GoalNode goal,
                                     String capability, Map<String, Object> output,
                                     TypedEntityReference entity, boolean accepted) {
        String implementationId = goal.id() + "-impl";
        String acceptanceId = goal.id() + "-accept";
        tasks.add(task(implementationId, goal.id(), capability, List.of()));
        tasks.add(task(acceptanceId, goal.id(), "planner.goal.accept", List.of(implementationId)));
        TypedTaskResult implementation = TypedTaskResult.success(implementationId, OUTPUT, output,
                "provider", entity.entityId(), List.of(entity), List.of("ev-" + implementationId));
        TypedTaskResult acceptance = TypedTaskResult.success(acceptanceId, OUTPUT,
                Map.of("accepted", accepted), "evaluator", goal.id(), List.of(),
                List.of("ev-" + acceptanceId));
        states.add(new DagTaskState(implementationId, DagTaskStatus.COMPLETED, 1, "", "", "",
                implementation, TaskEvaluation.pass(implementationId)));
        states.add(new DagTaskState(acceptanceId, DagTaskStatus.COMPLETED, 1, "", "", "",
                acceptance, TaskEvaluation.pass(acceptanceId)));
    }

    private static void addFailed(List<PlanTask> tasks, List<DagTaskState> states, GoalNode goal) {
        String implementationId = goal.id() + "-impl";
        String acceptanceId = goal.id() + "-accept";
        tasks.add(task(implementationId, goal.id(), "music.track.search", List.of()));
        tasks.add(task(acceptanceId, goal.id(), "planner.goal.accept", List.of(implementationId)));
        TypedTaskResult leaked = TypedTaskResult.success(implementationId, OUTPUT,
                Map.of("tracks", List.of(Map.of("name", "FAILED_RESULT_LEAK"))), "provider", "bad",
                List.of(entity(GoalTargetType.TRACK, "FAILED_RESULT_LEAK", "bad-track")), List.of("ev-bad"));
        TaskEvaluation failed = new TaskEvaluation(implementationId, EvaluationDecision.FAIL,
                List.of(new EvaluationFinding("FAILED", EvaluationDecision.FAIL, implementationId,
                        "执行失败")), "", "");
        states.add(new DagTaskState(implementationId, DagTaskStatus.FAILED, 1, "执行失败", "", "",
                leaked, failed));
        states.add(new DagTaskState(acceptanceId, DagTaskStatus.SKIPPED, 0, "依赖失败", "", "", null));
    }

    private static PlanTask task(String id, String goalId, String capability, List<String> dependencies) {
        return new PlanTask(id, id, capability, List.of(goalId), Map.of(), dependencies,
                List.of(), 1);
    }

    private static TypedEntityReference entity(GoalTargetType type, String name, String id) {
        return new TypedEntityReference(type, name, "provider", id);
    }

    private record Fixture(UserGoalGraph graph, DagExecutionSnapshot snapshot) { }
}
