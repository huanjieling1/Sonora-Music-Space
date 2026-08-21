package com.example.agent.agent.evaluation;

import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.GoalConstraint;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.planner.SafeJsonPath;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Evaluates goal coverage, user constraints and final-answer grounding after task acceptance. */
@Component
public final class GoalEvaluator {
    private static final String ACCEPT_CAPABILITY = "planner.goal.accept";
    private final SafeJsonPath jsonPath;

    public GoalEvaluator(SafeJsonPath jsonPath) {
        this.jsonPath = jsonPath;
    }

    public WorkflowEvaluation evaluate(UserGoalGraph graph, CompiledPlan plan,
                                       Map<String, TaskEvaluation> taskEvaluations,
                                       Map<String, TypedTaskResult> taskResults,
                                       List<GroundedClaim> finalClaims) {
        if (graph == null || plan == null) throw new IllegalArgumentException("目标图和编译计划不能为空");
        Map<String, TaskEvaluation> evaluations = taskEvaluations == null ? Map.of() : Map.copyOf(taskEvaluations);
        Map<String, TypedTaskResult> results = taskResults == null ? Map.of() : Map.copyOf(taskResults);
        ArrayList<GoalEvaluation> goals = new ArrayList<>();
        ArrayList<EvaluationFinding> workflowFindings = new ArrayList<>();
        EvaluationDecision workflowDecision = EvaluationDecision.PASS;

        if (!graph.graphId().equals(plan.goalGraphId())) {
            workflowFindings.add(finding("GOAL_GRAPH_PLAN_MISMATCH", EvaluationDecision.FAIL,
                    plan.planId().toString(), "编译计划不属于当前目标图"));
            workflowDecision = EvaluationDecision.FAIL;
        }

        for (GoalNode goal : graph.goals()) {
            GoalEvaluation evaluation = evaluateGoal(goal, plan, evaluations, results);
            goals.add(evaluation);
            workflowDecision = EvaluationDecision.combine(workflowDecision, evaluation.decision());
        }

        LinkedHashSet<String> graphGoalIds = new LinkedHashSet<>();
        graph.goals().forEach(goal -> graphGoalIds.add(goal.id()));
        plan.tasks().stream().flatMap(task -> task.goalIds().stream()).filter(id -> !graphGoalIds.contains(id))
                .distinct().forEach(id -> workflowFindings.add(finding("PLAN_REFERENCES_UNKNOWN_GOAL",
                        EvaluationDecision.REPLAN, id, "计划引用了目标图中不存在的目标")));

        Set<String> knownEvidence = results.values().stream().flatMap(result -> result.evidenceIds().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (GroundedClaim claim : finalClaims == null ? List.<GroundedClaim>of() : finalClaims) {
            if (claim.evidenceIds().isEmpty() || claim.evidenceIds().stream().anyMatch(id -> !knownEvidence.contains(id))) {
                workflowFindings.add(finding("UNGROUNDED_FINAL_CLAIM", EvaluationDecision.REVISE,
                        claim.text(), "最终结果包含没有任务证据支撑的结论"));
            }
        }
        for (EvaluationFinding finding : workflowFindings) {
            workflowDecision = EvaluationDecision.combine(workflowDecision, finding.decision());
        }
        return new WorkflowEvaluation(workflowDecision, evaluations, goals, workflowFindings);
    }

    private GoalEvaluation evaluateGoal(GoalNode goal, CompiledPlan plan,
                                        Map<String, TaskEvaluation> evaluations,
                                        Map<String, TypedTaskResult> results) {
        List<PlanTask> linked = plan.tasks().stream().filter(task -> task.goalIds().contains(goal.id())).toList();
        List<PlanTask> implementations = linked.stream()
                .filter(task -> !ACCEPT_CAPABILITY.equals(task.capabilityId())).toList();
        List<PlanTask> acceptances = linked.stream()
                .filter(task -> ACCEPT_CAPABILITY.equals(task.capabilityId())).toList();
        ArrayList<EvaluationFinding> findings = new ArrayList<>();

        if (!goal.missingSlots().isEmpty()) {
            goal.missingSlots().forEach(slot -> findings.add(finding("GOAL_INPUT_MISSING",
                    EvaluationDecision.ASK_USER, slot, "目标缺少用户输入：" + slot)));
        }
        if (implementations.isEmpty()) {
            findings.add(finding("GOAL_IMPLEMENTATION_OMITTED", EvaluationDecision.REPLAN,
                    goal.id(), "计划遗漏了该子目标的实现任务"));
        }
        if (acceptances.isEmpty()) {
            findings.add(finding("GOAL_ACCEPTANCE_OMITTED", EvaluationDecision.REPLAN,
                    goal.id(), "计划遗漏了该子目标的验收任务"));
        }

        for (PlanTask task : linked) {
            TaskEvaluation evaluation = evaluations.get(task.id());
            if (evaluation == null) {
                findings.add(finding("TASK_NOT_EVALUATED", EvaluationDecision.REPLAN,
                        task.id(), "目标关联任务尚未通过任务级验收"));
            } else if (evaluation.decision() != EvaluationDecision.PASS) {
                findings.add(finding("TASK_EVALUATION_NOT_PASS", evaluation.decision(),
                        task.id(), "目标关联任务未通过：" + evaluation.decision()));
            }
            if (!results.containsKey(task.id())) {
                findings.add(finding("TASK_RESULT_MISSING", EvaluationDecision.REPLAN,
                        task.id(), "目标关联任务没有结构化结果"));
            }
        }

        for (PlanTask acceptance : acceptances) {
            TypedTaskResult result = results.get(acceptance.id());
            if (result != null && (!result.successful() || !(result.output() instanceof Map<?, ?> output)
                    || !Boolean.TRUE.equals(output.get("accepted")))) {
                findings.add(finding("GOAL_NOT_ACCEPTED", EvaluationDecision.REPLAN,
                        goal.id(), "目标验收任务没有返回 accepted=true"));
            }
        }

        List<Object> outputs = implementations.stream().map(PlanTask::id).map(results::get)
                .filter(java.util.Objects::nonNull).filter(TypedTaskResult::successful)
                .map(TypedTaskResult::output).toList();
        for (GoalConstraint constraint : goal.constraints()) {
            Object actual = constraintValue(constraint.field(), outputs);
            Object expected = constraint.expected() instanceof ValueExpression.Literal literal ? literal.value() : null;
            boolean passed = TaskEvaluator.compare(actual, expected, constraint.operator().name());
            if (!passed && constraint.hard()) {
                findings.add(finding("GOAL_CONSTRAINT_FAILED", EvaluationDecision.REPLAN,
                        constraint.field(), constraint.description().isBlank()
                                ? "目标硬约束未满足：" + constraint.field() : constraint.description()));
            }
        }

        EvaluationDecision decision = findings.stream().map(EvaluationFinding::decision)
                .reduce(EvaluationDecision.PASS, EvaluationDecision::combine);
        return new GoalEvaluation(goal.id(), decision, linked.stream().map(PlanTask::id).toList(), findings);
    }

    private Object constraintValue(String field, List<Object> outputs) {
        String normalized = field.toLowerCase(Locale.ROOT);
        if (normalized.equals("count") || normalized.equals("quantity") || normalized.equals("数量")) {
            for (Object output : outputs) {
                if (!(output instanceof Map<?, ?> map)) continue;
                for (String key : List.of("tracks", "playlists", "entries")) {
                    if (map.get(key) instanceof Collection<?> values) return values.size();
                }
                for (String key : List.of("queuedCount", "count")) if (map.get(key) instanceof Number) return map.get(key);
            }
            return null;
        }
        if (normalized.equals("scene") || normalized.equals("场景")) {
            return outputs.stream().map(String::valueOf).reduce((left, right) -> left + " " + right).orElse(null);
        }
        if (normalized.equals("time") || normalized.equals("window") || normalized.equals("period")
                || normalized.equals("时间") || normalized.equals("周期")) {
            for (Object output : outputs) {
                if (!(output instanceof Map<?, ?> map)) continue;
                for (String key : List.of("window", "time", "period")) if (map.get(key) != null) return map.get(key);
            }
            return null;
        }
        String path = field.startsWith("$") ? field : "$." + field;
        for (Object output : outputs) {
            SafeJsonPath.JsonPathResult value = jsonPath.read(output, path);
            if (value.found()) return value.value();
        }
        return null;
    }

    private static EvaluationFinding finding(String code, EvaluationDecision decision,
                                             String subject, String message) {
        return new EvaluationFinding(code, decision, subject, message);
    }
}
