package com.example.agent.orchestration.replanning;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.evaluation.TaskEvaluation;
import com.example.agent.skill.AgentSkillRegistry;
import com.example.agent.orchestration.dag.DagTaskState;
import com.example.agent.orchestration.dag.DagTaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BoundedReplannerTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final BoundedReplanner replanner = new BoundedReplanner(registry,
            new ObjectMapper().findAndRegisterModules());

    @Test
    void locatesOnlyFailedDownstreamAndPreservesAcceptedResultsAndCriteria() {
        CompiledPlan plan = readOnlyPlan(2);
        Map<String, DagTaskState> states = states(plan, "search", 1);
        ReplanRequest request = replanner.prepare(UUID.randomUUID(), plan, "search",
                "PROVIDER_UNAVAILABLE", "provider down", 1, states, Map.of(), Set.of(), Set.of());
        AtomicReference<ReplanRequest> received = new AtomicReference<>();

        ReplanResult result = replanner.replan(request, value -> {
            received.set(value);
            return ReplanProposal.replace(alternative(value, "search", "备用查询"), "改用备用查询参数");
        });

        assertThat(result.kind()).isEqualTo(ReplanResult.Kind.APPLIED);
        assertThat(result.replacedTaskIds()).containsExactlyInAnyOrder("search", "search-accept");
        assertThat(result.preservedTaskIds()).containsExactlyInAnyOrder("artist", "artist-accept");
        assertThat(received.get().errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(received.get().acceptanceCriteria().get("search")).isEqualTo(
                plan.tasks().stream().filter(task -> task.id().equals("search")).findFirst().orElseThrow()
                        .acceptanceCriteria());
        assertThat(result.updatedPlan().tasks()).filteredOn(task -> task.id().equals("artist"))
                .containsExactly(plan.tasks().get(0));
    }

    @Test
    void rejectsSameFailedPlanAndStopsAtConfiguredReplanLimit() {
        CompiledPlan plan = readOnlyPlan(1);
        Map<String, DagTaskState> states = states(plan, "search", 1);
        ReplanRequest same = replanner.prepare(UUID.randomUUID(), plan, "search",
                "PROVIDER_FAILURE", "failed", 1, states, Map.of(), Set.of(), Set.of());

        ReplanResult identical = replanner.replan(same, request -> ReplanProposal.replace(
                request.currentPlan().tasks().stream()
                        .filter(task -> request.failedSubgraphTaskIds().contains(task.id())).toList(), "same"));
        ReplanRequest exhausted = replanner.prepare(UUID.randomUUID(), plan, "search",
                "PROVIDER_FAILURE", "failed", 2, states, Map.of(), Set.of(), Set.of());
        ReplanResult overLimit = replanner.replan(exhausted,
                request -> ReplanProposal.replace(alternative(request, "search", "never"), "never"));

        assertThat(identical.kind()).isEqualTo(ReplanResult.Kind.FAIL);
        assertThat(identical.message()).contains("相同");
        assertThat(overLimit.kind()).isEqualTo(ReplanResult.Kind.FAIL);
        assertThat(overLimit.message()).contains("最大重规划次数");
    }

    @Test
    void neverReplaysAttemptedSideEffectWithoutExplicitApproval() {
        CompiledPlan plan = mutationPlan();
        Map<String, DagTaskState> states = Map.of(
                "queue", new DagTaskState("queue", DagTaskStatus.FAILED, 1,
                        "unknown state", "", "idem", null, null, "STATE_UNKNOWN", false),
                "queue-accept", new DagTaskState("queue-accept", DagTaskStatus.PENDING,
                        0, "", "", "", null));
        ReplanRequest request = replanner.prepare(UUID.randomUUID(), plan, "queue",
                "STATE_UNKNOWN", "unknown state", 1, states, Map.of(), Set.of(), Set.of());
        java.util.concurrent.atomic.AtomicInteger strategyCalls = new java.util.concurrent.atomic.AtomicInteger();

        ReplanResult result = replanner.replan(request, value -> {
            strategyCalls.incrementAndGet();
            return ReplanProposal.fail("should not run");
        });

        assertThat(result.kind()).isEqualTo(ReplanResult.Kind.ASK_USER);
        assertThat(result.waitingSlot()).isEqualTo("replan.replay.queue");
        assertThat(strategyCalls).hasValue(0);
    }

    private CompiledPlan readOnlyPlan(int maxReplans) {
        AcceptanceCriterion artistCriterion = criterion("artist-output", "$.artist");
        AcceptanceCriterion searchCriterion = criterion("tracks-output", "$.tracks");
        PlanTask artist = new PlanTask("artist", "解析歌手", "profile.artist.resolve", List.of("g1"),
                Map.of("profile", ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile")),
                List.of(), List.of(artistCriterion), 1);
        PlanTask artistAccept = accept("artist-accept", "g1", "artist");
        PlanTask search = new PlanTask("search", "搜索歌曲", "music.track.search", List.of("g2"),
                Map.of("query", ValueExpression.taskOutput(ValueType.STRING, "artist", "$.artistName")),
                List.of("artist-accept"), List.of(searchCriterion), 1);
        PlanTask searchAccept = accept("search-accept", "g2", "search");
        return new CompiledPlan("1.0", UUID.randomUUID(), UUID.randomUUID(),
                List.of(artist, artistAccept, search, searchAccept),
                List.of(List.of("artist"), List.of("artist-accept"), List.of("search"), List.of("search-accept")),
                maxReplans);
    }

    private CompiledPlan mutationPlan() {
        PlanTask queue = new PlanTask("queue", "加入队列", "music.queue.add", List.of("g"),
                Map.of("tracks", ValueExpression.literal(ValueType.ARRAY, List.of(Map.of("id", "t1")))),
                List.of(), List.of(criterion("state", "$.success")), 1);
        PlanTask accept = accept("queue-accept", "g", "queue");
        return new CompiledPlan("1.0", UUID.randomUUID(), UUID.randomUUID(), List.of(queue, accept),
                List.of(List.of("queue"), List.of("queue-accept")), 2);
    }

    private Map<String, DagTaskState> states(CompiledPlan plan, String failedTask, int attempts) {
        java.util.LinkedHashMap<String, DagTaskState> values = new java.util.LinkedHashMap<>();
        for (PlanTask task : plan.tasks()) {
            if (task.id().equals(failedTask)) {
                values.put(task.id(), new DagTaskState(task.id(), DagTaskStatus.FAILED, attempts,
                        "provider failed", "", "", null, null, "PROVIDER_UNAVAILABLE", false));
            } else if (task.id().equals("artist") || task.id().equals("artist-accept")) {
                TypedTaskResult result = TypedTaskResult.success(task.id(),
                        registry.find(task.capabilityId()).orElseThrow().outputSchema(), Map.of("kept", true),
                        "TEST", "", List.of(), List.of("ev-" + task.id()));
                values.put(task.id(), new DagTaskState(task.id(), DagTaskStatus.COMPLETED, 1,
                        "", "", "", result, TaskEvaluation.pass(task.id()), "", false));
            } else {
                values.put(task.id(), new DagTaskState(task.id(), DagTaskStatus.PENDING,
                        0, "", "", "", null));
            }
        }
        return Map.copyOf(values);
    }

    private static List<PlanTask> alternative(ReplanRequest request, String changedTaskId, String title) {
        return request.currentPlan().tasks().stream()
                .filter(task -> request.failedSubgraphTaskIds().contains(task.id()))
                .map(task -> task.id().equals(changedTaskId)
                        ? new PlanTask(task.id(), title, task.capabilityId(), task.goalIds(),
                        Map.of("query", ValueExpression.literal(ValueType.STRING, "Mili live")),
                        task.dependencies(), task.activationConditions(), task.acceptanceCriteria(), task.maxAttempts())
                        : task).toList();
    }

    private static PlanTask accept(String id, String goalId, String dependency) {
        return new PlanTask(id, "验收", "planner.goal.accept", List.of(goalId), Map.of(
                "goalId", ValueExpression.literal(ValueType.STRING, goalId),
                "result", ValueExpression.taskOutput(ValueType.OBJECT, dependency, "$"),
                "criteria", ValueExpression.literal(ValueType.ARRAY, List.of())),
                List.of(dependency), List.of(criterion(id + "-accepted", "$.accepted")), 1);
    }

    private static AcceptanceCriterion criterion(String id, String subject) {
        return new AcceptanceCriterion(id, subject.equals("$.success")
                ? AcceptanceCriterion.Type.STATE_CHANGE : AcceptanceCriterion.Type.OUTPUT_PRESENT,
                subject, null, true, id, Map.of());
    }
}
