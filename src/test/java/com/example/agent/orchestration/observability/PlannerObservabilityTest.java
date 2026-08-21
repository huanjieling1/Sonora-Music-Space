package com.example.agent.orchestration.observability;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.goal.DeterministicMusicGoalParser;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanSynthesisException;
import com.example.agent.agent.planner.PlanValidationContext;
import com.example.agent.agent.planner.PlanValidator;
import com.example.agent.config.PlannerOperationsProperties;
import com.example.agent.skill.AgentSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerObservabilityTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));

    @Test
    void recordsRedactedGraphPlanTaskEvaluationAndDuration() throws Exception {
        PlannerOperationsProperties properties = new PlannerOperationsProperties();
        properties.setTaskCountAlertThreshold(1);
        PlannerObservability observability = new PlannerObservability(properties, mapper);
        var graph = new DeterministicMusicGoalParser().parse("搜索《绝密歌曲》");
        CompiledPlan plan = new PlanCompiler(new PlanValidator(registry)).compile(graph,
                new GenericPlanSynthesizer(registry).synthesize(graph),
                PlanValidationContext.standard("user-1"));
        PlanTask task = plan.tasks().get(0);

        observability.compiled(graph, plan, "workflow-1");
        observability.taskStarted(UUID.randomUUID(), task, CapabilitySideEffect.READ_ONLY, 1, "");
        observability.taskFinished(UUID.randomUUID(), task, CapabilitySideEffect.READ_ONLY,
                "", 17, "COMPLETED", "", com.example.agent.agent.evaluation.TaskEvaluation.pass(task.id()));

        String json = mapper.writeValueAsString(observability.recentEvents());
        assertThat(json).doesNotContain("搜索《绝密歌曲》", "绝密歌曲");
        assertThat(json).contains("requestSha256", "inputSources", "durationMillis", "LITERAL");
        assertThat(observability.recentAlerts()).extracting(PlannerOperationalAlert::type)
                .contains(PlannerAlertType.ABNORMAL_TASK_COUNT);
    }

    @Test
    void raisesRawRequestDuplicateSideEffectAndCycleAlerts() {
        PlannerObservability observability = new PlannerObservability(new PlannerOperationsProperties(), mapper);
        var graph = new DeterministicMusicGoalParser().parse("搜索《晴天》");
        CompiledPlan safe = new PlanCompiler(new PlanValidator(registry)).compile(graph,
                new GenericPlanSynthesizer(registry).synthesize(graph),
                PlanValidationContext.standard("user-1"));
        ArrayList<PlanTask> tasks = new ArrayList<>(safe.tasks());
        PlanTask original = tasks.get(0);
        tasks.set(0, new PlanTask(original.id(), original.title(), original.capabilityId(),
                original.goalIds(), Map.of("query", ValueExpression.literal(ValueType.STRING,
                graph.originalRequest())), original.dependencies(), original.activationConditions(),
                original.acceptanceCriteria(), original.maxAttempts()));
        CompiledPlan poisoned = new CompiledPlan(safe.schemaVersion(), safe.planId(), safe.goalGraphId(),
                tasks, safe.executionStages(), safe.maxReplans());

        observability.compiled(graph, poisoned, "workflow-alert");
        observability.taskFinished(UUID.randomUUID(), original, CapabilitySideEffect.PERSISTENT_MUTATION,
                "same-key", 5, "COMPLETED", "", null);
        observability.taskStarted(UUID.randomUUID(), original, CapabilitySideEffect.PERSISTENT_MUTATION,
                2, "same-key");
        observability.planningRejected(graph, new PlanSynthesisException("计划存在循环依赖"),
                "workflow-cycle");

        assertThat(observability.recentAlerts()).extracting(PlannerOperationalAlert::type)
                .contains(PlannerAlertType.RAW_REQUEST_FORWARDING,
                        PlannerAlertType.DUPLICATE_SIDE_EFFECT, PlannerAlertType.WORKFLOW_CYCLE);
    }
}
