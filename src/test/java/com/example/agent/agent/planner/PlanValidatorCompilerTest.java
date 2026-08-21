package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.PlanDraft;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.goal.DeterministicMusicGoalParser;
import com.example.agent.skill.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanValidatorCompilerTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final GenericPlanSynthesizer synthesizer = new GenericPlanSynthesizer(registry);
    private final PlanValidator validator = new PlanValidator(registry);
    private final PlanCompiler compiler = new PlanCompiler(validator);
    private final DeterministicMusicGoalParser parser = new DeterministicMusicGoalParser();

    @Test
    void validatesAndCompilesACompoundPlanIntoImmutableTopologicalStages() {
        UserGoalGraph graph = graph();
        PlanDraft draft = synthesizer.synthesize(graph);

        PlanValidationResult validation = validator.validate(graph, draft, context());
        CompiledPlan compiled = compiler.compile(graph, draft, context());

        assertThat(validation.valid()).isTrue();
        assertThat(validation.issues()).isEmpty();
        assertThat(validation.estimatedCostUnits()).isPositive();
        assertThat(validation.worstCaseDurationSeconds()).isPositive();
        assertThat(compiled.tasks()).isEqualTo(draft.tasks());
        assertThat(compiled.executionStages()).containsExactly(
                List.of("task-1-execute"), List.of("task-1-accept"),
                List.of("task-2-execute"), List.of("task-2-accept"),
                List.of("task-3-execute"), List.of("task-3-accept"),
                List.of("task-4-execute"), List.of("task-4-accept"));
        assertThatThrownBy(() -> compiled.tasks().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> compiled.executionStages().get(0).add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateIdsCyclesAndUnknownTaskReferences() {
        UserGoalGraph graph = graph();
        PlanDraft base = synthesizer.synthesize(graph);

        ArrayList<PlanTask> duplicated = new ArrayList<>(base.tasks());
        duplicated.add(base.tasks().get(0));
        assertCodes(graph, draft(base, duplicated), "DUPLICATE_TASK_ID");

        ArrayList<PlanTask> cyclic = new ArrayList<>(base.tasks());
        cyclic.set(0, withDependencies(cyclic.get(0), List.of("task-1-accept")));
        assertCodes(graph, draft(base, cyclic), "CYCLIC_DEPENDENCY");

        ArrayList<PlanTask> dangling = new ArrayList<>(base.tasks());
        dangling.set(0, withDependencies(dangling.get(0), List.of("missing-task")));
        assertCodes(graph, draft(base, dangling), "UNKNOWN_TASK_REFERENCE");
    }

    @Test
    void rejectsUnknownCapabilitiesTypeMismatchesAndUnboundInputs() {
        UserGoalGraph graph = graph();
        PlanDraft base = synthesizer.synthesize(graph);

        ArrayList<PlanTask> unknownCapability = new ArrayList<>(base.tasks());
        unknownCapability.set(0, withCapability(unknownCapability.get(0), "tool.fakeImplementation"));
        assertCodes(graph, draft(base, unknownCapability), "CAPABILITY_NOT_REGISTERED");

        ArrayList<PlanTask> wrongType = new ArrayList<>(base.tasks());
        PlanTask queue = wrongType.get(6);
        wrongType.set(6, withInputs(queue, Map.of("tracks",
                ValueExpression.taskOutput(ValueType.OBJECT, "task-2-execute", "$.profile"))));
        assertCodes(graph, draft(base, wrongType), "INPUT_TYPE_MISMATCH");

        ArrayList<PlanTask> missing = new ArrayList<>(base.tasks());
        PlanTask recommend = missing.get(4);
        missing.set(4, withInputs(recommend, Map.of("limit", recommend.inputs().get("limit"))));
        assertCodes(graph, draft(base, missing), "UNBOUND_REQUIRED_INPUT");

        UserGoalGraph unresolvedGraph = parser.parse("推荐歌曲");
        PlanDraft unresolved = synthesizer.synthesize(unresolvedGraph);
        assertCodes(unresolvedGraph, unresolved, "UNRESOLVED_USER_INPUT");
    }

    @Test
    void rejectsMissingGoalImplementationAndAcceptanceCoverage() {
        UserGoalGraph graph = graph();
        PlanDraft base = synthesizer.synthesize(graph);

        List<PlanTask> withoutImplementation = base.tasks().stream()
                .filter(task -> !task.id().equals("task-2-execute")).toList();
        assertCodes(graph, draft(base, withoutImplementation), "GOAL_NOT_IMPLEMENTED");

        List<PlanTask> withoutAcceptance = base.tasks().stream()
                .filter(task -> !task.id().equals("task-2-accept")).toList();
        assertCodes(graph, draft(base, withoutAcceptance), "GOAL_WITHOUT_ACCEPTANCE_TASK");

        ArrayList<GoalNode> goals = new ArrayList<>(graph.goals());
        GoalNode first = goals.get(0);
        goals.set(0, new GoalNode(first.id(), first.title(), first.operation(), first.targetType(),
                first.inputs(), first.constraints(), List.of(), first.missingSlots(), first.requiresConfirmation()));
        UserGoalGraph noCriteriaGraph = new UserGoalGraph(graph.schemaVersion(), graph.graphId(),
                graph.originalRequest(), goals, graph.relations());
        ArrayList<PlanTask> noCriteriaTasks = new ArrayList<>(base.tasks());
        PlanTask implementation = noCriteriaTasks.get(0);
        noCriteriaTasks.set(0, new PlanTask(implementation.id(), implementation.title(),
                implementation.capabilityId(), implementation.goalIds(), implementation.inputs(),
                implementation.dependencies(), implementation.activationConditions(), List.of(),
                implementation.maxAttempts()));
        assertCodes(noCriteriaGraph, draft(base, noCriteriaTasks), "GOAL_WITHOUT_ACCEPTANCE_CRITERIA");
    }

    @Test
    void rejectsRawRequestForwardingAndCrossUserProfileAccess() {
        UserGoalGraph graph = graph();
        PlanDraft base = synthesizer.synthesize(graph);

        ArrayList<PlanTask> raw = new ArrayList<>(base.tasks());
        PlanTask recommend = raw.get(4);
        raw.set(4, withInputs(recommend, Map.of(
                "query", ValueExpression.literal(ValueType.STRING, graph.originalRequest()),
                "limit", recommend.inputs().get("limit"))));
        assertCodes(graph, draft(base, raw), "RAW_REQUEST_FORWARDING");

        ArrayList<PlanTask> crossUser = new ArrayList<>(base.tasks());
        PlanTask resolve = crossUser.get(0);
        crossUser.set(0, withInputs(resolve, Map.of("profile",
                ValueExpression.profileValue(ValueType.OBJECT, "$.users.other.profile"))));
        assertCodes(graph, draft(base, crossUser), "CROSS_USER_PROFILE_ACCESS");
    }

    @Test
    void rejectsUnauthorizedSideEffectsMissingExplicitIntentAndExceededBudgets() {
        UserGoalGraph graph = graph();
        PlanDraft draft = synthesizer.synthesize(graph);

        PlanValidationContext readOnly = new PlanValidationContext("user-1", true, true, false,
                Set.of(), Set.of(CapabilitySideEffect.READ_ONLY), 200, 300, 50);
        assertCodes(graph, draft, readOnly, "SIDE_EFFECT_NOT_ALLOWED");

        ArrayList<GoalNode> goals = new ArrayList<>(graph.goals());
        GoalNode queueGoal = goals.get(3);
        goals.set(3, new GoalNode(queueGoal.id(), queueGoal.title(), queueGoal.operation(),
                queueGoal.targetType(), queueGoal.inputs(), queueGoal.constraints(),
                queueGoal.acceptanceCriteria(), queueGoal.missingSlots(), false));
        UserGoalGraph noIntent = new UserGoalGraph(graph.schemaVersion(), graph.graphId(),
                graph.originalRequest(), goals, graph.relations());
        assertCodes(noIntent, draft, "EXPLICIT_INTENT_REQUIRED");

        PlanValidationContext tinyBudget = new PlanValidationContext("user-1", true, true, false,
                Set.of(), Set.of(CapabilitySideEffect.values()), 1, 10, 1);
        PlanValidationResult result = validator.validate(graph, draft, tinyBudget);
        assertThat(result.issues()).extracting(PlanValidationIssue::code)
                .contains("COST_BUDGET_EXCEEDED", "TIME_BUDGET_EXCEEDED", "RETRY_BUDGET_EXCEEDED");
    }

    @Test
    void compilerNeverEmitsACompiledPlanWhenValidationFails() {
        UserGoalGraph graph = graph();
        PlanDraft base = synthesizer.synthesize(graph);
        ArrayList<PlanTask> invalid = new ArrayList<>(base.tasks());
        invalid.set(0, withCapability(invalid.get(0), "missing.capability"));

        assertThatThrownBy(() -> compiler.compile(graph, draft(base, invalid), context()))
                .isInstanceOf(PlanValidationException.class)
                .satisfies(error -> assertThat(((PlanValidationException) error).issues())
                        .extracting(PlanValidationIssue::code).contains("CAPABILITY_NOT_REGISTERED"));
    }

    private UserGoalGraph graph() {
        return parser.parse("找出我最喜欢的歌手资料，再推荐三首他的歌并加入队列");
    }

    private static PlanValidationContext context() {
        return PlanValidationContext.standard("user-1");
    }

    private void assertCodes(UserGoalGraph graph, PlanDraft draft, String... codes) {
        assertCodes(graph, draft, context(), codes);
    }

    private void assertCodes(UserGoalGraph graph, PlanDraft draft, PlanValidationContext context,
                             String... codes) {
        assertThat(validator.validate(graph, draft, context).issues())
                .extracting(PlanValidationIssue::code).contains(codes);
    }

    private static PlanDraft draft(PlanDraft base, List<PlanTask> tasks) {
        return new PlanDraft(base.schemaVersion(), base.planId(), base.goalGraphId(), tasks, base.maxReplans());
    }

    private static PlanTask withDependencies(PlanTask task, List<String> dependencies) {
        return new PlanTask(task.id(), task.title(), task.capabilityId(), task.goalIds(), task.inputs(),
                dependencies, task.activationConditions(), task.acceptanceCriteria(), task.maxAttempts());
    }

    private static PlanTask withCapability(PlanTask task, String capabilityId) {
        return new PlanTask(task.id(), task.title(), capabilityId, task.goalIds(), task.inputs(),
                task.dependencies(), task.activationConditions(), task.acceptanceCriteria(), task.maxAttempts());
    }

    private static PlanTask withInputs(PlanTask task, Map<String, ValueExpression> inputs) {
        return new PlanTask(task.id(), task.title(), task.capabilityId(), task.goalIds(), inputs,
                task.dependencies(), task.activationConditions(), task.acceptanceCriteria(), task.maxAttempts());
    }
}
