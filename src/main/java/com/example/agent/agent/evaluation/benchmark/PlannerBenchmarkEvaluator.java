package com.example.agent.agent.evaluation.benchmark;

import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.goal.MusicGoalDecomposer;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Repeatable offline evaluator for understanding, compilation and grounded execution claims. */
@Component
public final class PlannerBenchmarkEvaluator {
    private final MusicGoalDecomposer decomposer;
    private final GenericPlanSynthesizer synthesizer;
    private final PlanCompiler compiler;

    public PlannerBenchmarkEvaluator(MusicGoalDecomposer decomposer,
                                     GenericPlanSynthesizer synthesizer,
                                     PlanCompiler compiler) {
        this.decomposer = decomposer;
        this.synthesizer = synthesizer;
        this.compiler = compiler;
    }

    public PlannerEvaluationReport evaluate(List<PlannerBenchmarkCase> cases,
                                            List<PlannerExecutionObservation> observations) {
        List<PlannerBenchmarkCase> corpus = cases == null ? List.of() : List.copyOf(cases);
        List<PlannerExecutionObservation> runs = observations == null ? List.of() : List.copyOf(observations);
        ArrayList<String> decompositionFailures = new ArrayList<>();
        ArrayList<String> compilationFailures = new ArrayList<>();
        int exact = 0;
        int compilable = 0;

        for (PlannerBenchmarkCase item : corpus) {
            UserGoalGraph graph;
            try {
                graph = decomposer.decompose(item.request());
            } catch (RuntimeException error) {
                decompositionFailures.add(item.id() + ": " + error.getMessage());
                compilationFailures.add(item.id() + ": decomposition failed");
                continue;
            }
            if (matches(item, graph)) exact++;
            else decompositionFailures.add(item.id() + ": expected=" + item.expectedGoals()
                    + ", actual=" + graph.goals().stream().map(goal ->
                    new PlannerBenchmarkCase.ExpectedGoal(goal.operation(), goal.targetType())).toList()
                    + ", expectedRelations=" + item.expectedRelationTypes()
                    + ", actualRelations=" + graph.relations().stream().map(GoalRelation::type).toList());
            try {
                Set<String> goalIds = graph.goals().stream().map(GoalNode::id)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                PlanValidationContext context = new PlanValidationContext("benchmark-user", true, true, true,
                        goalIds, Set.of(CapabilitySideEffect.values()), 1_000, 1_000, 200);
                compiler.compile(graph, synthesizer.synthesize(graph), context);
                compilable++;
            } catch (RuntimeException error) {
                compilationFailures.add(item.id() + ": " + error.getMessage());
            }
        }

        int satisfiable = (int) runs.stream().filter(PlannerExecutionObservation::actuallySatisfied).count();
        int completed = (int) runs.stream().filter(value -> value.actuallySatisfied()
                && value.reportedCompleted()).count();
        int unsatisfied = runs.size() - satisfiable;
        int falseSuccess = (int) runs.stream().filter(value -> !value.actuallySatisfied()
                && value.reportedCompleted()).count();
        return new PlannerEvaluationReport(corpus.size(), exact, compilable, satisfiable, completed,
                unsatisfied, falseSuccess, percentage(exact, corpus.size()),
                percentage(compilable, corpus.size()), percentage(completed, satisfiable),
                percentage(falseSuccess, unsatisfied), decompositionFailures, compilationFailures);
    }

    private static boolean matches(PlannerBenchmarkCase item, UserGoalGraph graph) {
        List<PlannerBenchmarkCase.ExpectedGoal> actualGoals = graph.goals().stream()
                .map(goal -> new PlannerBenchmarkCase.ExpectedGoal(goal.operation(), goal.targetType())).toList();
        return item.expectedGoals().equals(actualGoals)
                && frequencies(item.expectedRelationTypes()).equals(frequencies(graph.relations().stream()
                .map(GoalRelation::type).toList()));
    }

    private static Map<GoalRelation.Type, Integer> frequencies(List<GoalRelation.Type> values) {
        EnumMap<GoalRelation.Type, Integer> result = new EnumMap<>(GoalRelation.Type.class);
        for (GoalRelation.Type value : values) result.merge(value, 1, Integer::sum);
        return Map.copyOf(result);
    }

    private static double percentage(int numerator, int denominator) {
        if (denominator == 0) return 0.0;
        return Math.round(numerator * 10_000.0 / denominator) / 100.0;
    }
}
