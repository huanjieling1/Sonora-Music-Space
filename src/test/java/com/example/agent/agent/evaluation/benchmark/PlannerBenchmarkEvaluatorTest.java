package com.example.agent.agent.evaluation.benchmark;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.goal.MusicGoalDecomposer;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidator;
import com.example.agent.skill.AgentSkillRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerBenchmarkEvaluatorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final PlannerBenchmarkEvaluator evaluator = new PlannerBenchmarkEvaluator(
            new MusicGoalDecomposer(), new GenericPlanSynthesizer(registry),
            new PlanCompiler(new PlanValidator(registry)));

    @Test
    void evaluatesVersionedSingleDualAndThreeToSixIntentCorpus() throws Exception {
        List<PlannerBenchmarkCase> corpus = corpus();

        PlannerEvaluationReport report = evaluator.evaluate(corpus, executionObservations());

        assertThat(corpus).hasSize(18);
        assertThat(corpus.stream().filter(value -> value.tier() == PlannerBenchmarkCase.Tier.SINGLE)).hasSize(6);
        assertThat(corpus.stream().filter(value -> value.tier() == PlannerBenchmarkCase.Tier.DUAL)).hasSize(6);
        assertThat(corpus.stream().filter(value -> value.tier() == PlannerBenchmarkCase.Tier.MULTI)).hasSize(6);
        assertThat(corpus.stream().filter(value -> value.tier() == PlannerBenchmarkCase.Tier.MULTI)
                .map(value -> value.expectedGoals().size())).contains(3, 4, 5, 6);
        assertThat(corpus.stream().flatMap(value -> value.expectedRelationTypes().stream()))
                .contains(com.example.agent.agent.contract.planning.GoalRelation.Type.SEQUENCE,
                        com.example.agent.agent.contract.planning.GoalRelation.Type.PARALLEL,
                        com.example.agent.agent.contract.planning.GoalRelation.Type.CONDITIONAL);
        assertThat(report.goalDecompositionAccuracy())
                .as("failures=%s", report.decompositionFailures()).isEqualTo(100.0);
        assertThat(report.planCompilability())
                .as("failures=%s", report.compilationFailures()).isEqualTo(100.0);
        assertThat(report.goalCompletionRate()).isEqualTo(100.0);
        assertThat(report.falseSuccessRate()).isZero();
    }

    private List<PlannerBenchmarkCase> corpus() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/planner-evaluation-corpus.json")) {
            if (stream == null) throw new IllegalStateException("planner evaluation corpus missing");
            return mapper.readValue(stream, new TypeReference<>() {});
        }
    }

    private static List<PlannerExecutionObservation> executionObservations() {
        return List.of(
                new PlannerExecutionObservation("serial-read-success", true, true),
                new PlannerExecutionObservation("parallel-read-success", true, true),
                new PlannerExecutionObservation("pause-resume-success", true, true),
                new PlannerExecutionObservation("local-replan-success", true, true),
                new PlannerExecutionObservation("malformed-tool-result", false, false),
                new PlannerExecutionObservation("timeout", false, false),
                new PlannerExecutionObservation("partial-failure-branch", false, false),
                new PlannerExecutionObservation("confirmation-rejected", false, false),
                new PlannerExecutionObservation("unproven-side-effect", false, false));
    }
}
