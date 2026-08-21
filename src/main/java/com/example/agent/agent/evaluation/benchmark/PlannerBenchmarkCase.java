package com.example.agent.agent.evaluation.benchmark;

import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.GoalTargetType;

import java.util.List;

/** One versioned, human-reviewable planner benchmark item. */
public record PlannerBenchmarkCase(
        String id,
        Tier tier,
        String request,
        List<ExpectedGoal> expectedGoals,
        List<GoalRelation.Type> expectedRelationTypes,
        List<String> tags
) {
    public PlannerBenchmarkCase {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("基准样本 ID 不能为空");
        if (tier == null) throw new IllegalArgumentException("基准样本必须声明意图层级");
        if (request == null || request.isBlank()) throw new IllegalArgumentException("基准请求不能为空");
        expectedGoals = expectedGoals == null ? List.of() : List.copyOf(expectedGoals);
        if (expectedGoals.isEmpty()) throw new IllegalArgumentException("基准样本必须声明预期 Goal");
        expectedRelationTypes = expectedRelationTypes == null ? List.of() : List.copyOf(expectedRelationTypes);
        tags = tags == null ? List.of() : tags.stream().filter(value -> value != null && !value.isBlank())
                .map(String::strip).distinct().toList();
    }

    public enum Tier { SINGLE, DUAL, MULTI }

    public record ExpectedGoal(GoalOperation operation, GoalTargetType target) {
        public ExpectedGoal {
            if (operation == null || target == null) {
                throw new IllegalArgumentException("预期 Goal 必须声明 operation 和 target");
            }
        }
    }
}
