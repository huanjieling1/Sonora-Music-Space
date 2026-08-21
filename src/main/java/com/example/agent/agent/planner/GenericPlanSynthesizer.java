package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilityFieldSchema;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.GoalConstraint;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.PlanDraft;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Capability-driven, domain-neutral plan synthesis. It only sees logical capability contracts and
 * emits a bounded PlanDraft; concrete tools remain an execution/compiler concern.
 */
@Component
public final class GenericPlanSynthesizer {
    public static final int MAX_TASKS = 24;
    public static final int MAX_DEPTH = 12;
    private static final String ACCEPT_CAPABILITY = "planner.goal.accept";

    private final AgentCapabilityRegistry registry;

    public GenericPlanSynthesizer(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public PlanDraft synthesize(UserGoalGraph graph) {
        return synthesize(graph, CapabilityRegistrySnapshot.from(registry));
    }

    public PlanDraft synthesize(UserGoalGraph graph, CapabilityRegistrySnapshot snapshot) {
        if (graph == null) throw new PlanSynthesisException("UserGoalGraph 不能为空");
        if (snapshot == null || snapshot.capabilities().isEmpty()) {
            throw new PlanSynthesisException("Capability Registry 快照不能为空");
        }
        if (graph.goals().size() * 2 > MAX_TASKS) {
            throw new PlanSynthesisException("计划任务数超过上限 " + MAX_TASKS);
        }
        Map<String, GoalNode> goals = indexGoals(graph.goals());
        validateRelations(graph.relations(), goals.keySet());
        Map<String, PlannerCapability> selected = selectCapabilities(graph.goals(), snapshot);
        PlannerCapability acceptCapability = snapshot.byId().get(ACCEPT_CAPABILITY);
        if (acceptCapability == null) {
            throw new PlanSynthesisException("能力快照缺少目标验收能力 " + ACCEPT_CAPABILITY);
        }

        Map<String, String> executeIds = new LinkedHashMap<>();
        Map<String, String> acceptIds = new LinkedHashMap<>();
        for (int index = 0; index < graph.goals().size(); index++) {
            GoalNode goal = graph.goals().get(index);
            executeIds.put(goal.id(), "task-" + (index + 1) + "-execute");
            acceptIds.put(goal.id(), "task-" + (index + 1) + "-accept");
        }

        ArrayList<PlanTask> tasks = new ArrayList<>();
        for (GoalNode goal : graph.goals()) {
            PlannerCapability capability = selected.get(goal.id());
            LinkedHashSet<String> dependencies = dependencies(goal.id(), graph.relations(), acceptIds);
            List<ValueExpression> conditions = conditions(goal.id(), graph.relations());
            Map<String, ValueExpression> inputs = bindInputs(goal, capability, graph, selected,
                    executeIds, acceptIds, dependencies);
            List<AcceptanceCriterion> criteria = goalCriteria(goal);
            String executeId = executeIds.get(goal.id());
            tasks.add(new PlanTask(executeId, goal.title(), capability.id(), List.of(goal.id()), inputs,
                    List.copyOf(dependencies), conditions, criteria, capability.executionPolicy().maxAttempts()));

            String acceptId = acceptIds.get(goal.id());
            tasks.add(new PlanTask(acceptId, "验收：" + goal.title(), ACCEPT_CAPABILITY, List.of(goal.id()),
                    acceptanceInputs(goal, executeId, criteria), List.of(executeId), conditions,
                    List.of(new AcceptanceCriterion(acceptId + "-accepted",
                            AcceptanceCriterion.Type.GOAL_COVERAGE, "$.accepted",
                            ValueExpression.literal(ValueType.BOOLEAN, true), true,
                            "目标实现任务必须通过结构化验收", Map.of())),
                    acceptCapability.executionPolicy().maxAttempts()));
        }

        ensureBounded(tasks);
        ensureOriginalRequestNotForwarded(graph.originalRequest(), tasks);
        return new PlanDraft("1.0", UUID.randomUUID(), graph.graphId(), List.copyOf(tasks), 2);
    }

    private static Map<String, GoalNode> indexGoals(List<GoalNode> goals) {
        LinkedHashMap<String, GoalNode> result = new LinkedHashMap<>();
        for (GoalNode goal : goals) {
            if (result.putIfAbsent(goal.id(), goal) != null) {
                throw new PlanSynthesisException("目标标识重复：" + goal.id());
            }
        }
        return Map.copyOf(result);
    }

    private static void validateRelations(List<GoalRelation> relations, Set<String> goalIds) {
        for (GoalRelation relation : relations) {
            if (!goalIds.contains(relation.sourceGoalId()) || !goalIds.contains(relation.targetGoalId())) {
                throw new PlanSynthesisException("目标关系引用不存在的目标：" + relation);
            }
        }
    }

    private static Map<String, PlannerCapability> selectCapabilities(
            List<GoalNode> goals, CapabilityRegistrySnapshot snapshot) {
        LinkedHashMap<String, PlannerCapability> result = new LinkedHashMap<>();
        for (GoalNode goal : goals) {
            PlannerCapability selected = snapshot.capabilities().stream()
                    .filter(value -> !value.id().equals(ACCEPT_CAPABILITY))
                    .filter(value -> value.supportedOperations().contains(goal.operation()))
                    .filter(value -> value.supportedTargets().isEmpty()
                            || value.supportedTargets().contains(goal.targetType()))
                    .sorted(Comparator.comparingInt((PlannerCapability value) ->
                                    value.supportedTargets().contains(goal.targetType()) ? 0 : 1)
                            .thenComparing(PlannerCapability::id))
                    .findFirst().orElseThrow(() -> new PlanSynthesisException(
                            "没有已注册能力可实现目标 " + goal.id() + "（" + goal.operation()
                                    + "/" + goal.targetType() + "）"));
            result.put(goal.id(), selected);
        }
        return Map.copyOf(result);
    }

    private static LinkedHashSet<String> dependencies(String goalId, List<GoalRelation> relations,
                                                       Map<String, String> acceptIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        relations.stream().filter(value -> value.targetGoalId().equals(goalId))
                .filter(value -> value.type() != GoalRelation.Type.PARALLEL)
                .forEach(value -> result.add(acceptIds.get(value.sourceGoalId())));
        return result;
    }

    private static List<ValueExpression> conditions(String goalId, List<GoalRelation> relations) {
        return relations.stream().filter(value -> value.targetGoalId().equals(goalId))
                .filter(value -> value.type() == GoalRelation.Type.CONDITIONAL)
                .map(GoalRelation::condition).toList();
    }

    private static Map<String, ValueExpression> bindInputs(
            GoalNode goal, PlannerCapability capability, UserGoalGraph graph,
            Map<String, PlannerCapability> selected, Map<String, String> executeIds,
            Map<String, String> acceptIds, LinkedHashSet<String> dependencies) {
        LinkedHashMap<String, ValueExpression> result = new LinkedHashMap<>();
        for (Map.Entry<String, CapabilityFieldSchema> entry : capability.inputSchema().fields().entrySet()) {
            String field = entry.getKey();
            CapabilityFieldSchema schema = entry.getValue();
            ValueExpression binding = directBinding(goal, field);
            if (binding == null) {
                OutputBinding upstream = upstreamBinding(goal.id(), field, graph, selected, executeIds);
                if (upstream != null) {
                    binding = ValueExpression.taskOutput(schema.type(), upstream.taskId(), "$." + upstream.field());
                    dependencies.add(acceptIds.get(upstream.goalId()));
                }
            }
            if (binding == null && field.equals("profile")) {
                binding = ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile");
            }
            if (binding == null && field.equals("favorite")) {
                binding = ValueExpression.literal(ValueType.BOOLEAN, !goal.title().contains("取消"));
            }
            if (binding == null && schema.required()) {
                binding = ValueExpression.userInput(schema.type(), goal.id() + "." + field, true);
            }
            if (binding != null) result.put(field, binding);
        }
        return Map.copyOf(result);
    }

    private static ValueExpression directBinding(GoalNode goal, String field) {
        ValueExpression exact = goal.inputs().get(field);
        if (exact != null) return exact;
        if (field.equals("query") || field.equals("keyword") || field.equals("chartType")) {
            ArrayList<String> parts = new ArrayList<>();
            for (String name : List.of("trackTitle", "artistName")) {
                ValueExpression expression = goal.inputs().get(name);
                if (expression instanceof ValueExpression.Literal literal && literal.value() instanceof String text) {
                    parts.add(text);
                }
            }
            for (GoalConstraint constraint : goal.constraints()) {
                if (constraint.expected() instanceof ValueExpression.Literal literal
                        && literal.value() instanceof String text) parts.add(text);
            }
            if (!parts.isEmpty()) return ValueExpression.literal(ValueType.STRING,
                    String.join(" ", new LinkedHashSet<>(parts)));
        }
        return null;
    }

    private static OutputBinding upstreamBinding(
            String targetGoalId, String inputField, UserGoalGraph graph,
            Map<String, PlannerCapability> selected, Map<String, String> executeIds) {
        List<String> candidates = outputAliases(inputField);
        for (GoalRelation relation : graph.relations()) {
            if (!relation.targetGoalId().equals(targetGoalId)
                    || relation.type() == GoalRelation.Type.PARALLEL) continue;
            PlannerCapability producer = selected.get(relation.sourceGoalId());
            if (producer == null) continue;
            for (String field : candidates) {
                if (producer.outputSchema().fields().containsKey(field)) {
                    return new OutputBinding(relation.sourceGoalId(), executeIds.get(relation.sourceGoalId()), field);
                }
            }
        }
        return null;
    }

    private static List<String> outputAliases(String inputField) {
        return switch (inputField) {
            case "query", "keyword", "artistName" -> List.of("artistName", "canonicalName");
            case "tracks" -> List.of("tracks");
            case "track" -> List.of("track");
            case "profile" -> List.of("profile");
            default -> List.of(inputField);
        };
    }

    private static List<AcceptanceCriterion> goalCriteria(GoalNode goal) {
        if (!goal.acceptanceCriteria().isEmpty()) return goal.acceptanceCriteria();
        return List.of(new AcceptanceCriterion(goal.id() + "-output", AcceptanceCriterion.Type.OUTPUT_PRESENT,
                "$.result", null, true, "目标必须产生可验证结果", Map.of()));
    }

    private static Map<String, ValueExpression> acceptanceInputs(
            GoalNode goal, String executeId, List<AcceptanceCriterion> criteria) {
        List<Map<String, Object>> serialized = criteria.stream().map(GenericPlanSynthesizer::criterionValue).toList();
        return Map.of(
                "goalId", ValueExpression.literal(ValueType.STRING, goal.id()),
                "result", ValueExpression.taskOutput(ValueType.OBJECT, executeId, "$"),
                "criteria", ValueExpression.literal(ValueType.ARRAY, serialized));
    }

    private static Map<String, Object> criterionValue(AcceptanceCriterion criterion) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", criterion.id());
        value.put("type", criterion.type().name());
        value.put("subject", criterion.subject());
        value.put("required", criterion.required());
        value.put("description", criterion.description());
        return Map.copyOf(value);
    }

    private static void ensureBounded(List<PlanTask> tasks) {
        if (tasks.size() > MAX_TASKS) throw new PlanSynthesisException("计划任务数超过上限 " + MAX_TASKS);
        Map<String, PlanTask> byId = new LinkedHashMap<>();
        for (PlanTask task : tasks) {
            if (byId.putIfAbsent(task.id(), task) != null) {
                throw new PlanSynthesisException("计划任务标识重复：" + task.id());
            }
        }
        HashMap<String, Integer> memo = new HashMap<>();
        for (PlanTask task : tasks) {
            int depth = depth(task.id(), byId, memo, new HashSet<>());
            if (depth > MAX_DEPTH) throw new PlanSynthesisException("计划深度超过上限 " + MAX_DEPTH);
        }
    }

    private static int depth(String taskId, Map<String, PlanTask> tasks,
                             Map<String, Integer> memo, Set<String> visiting) {
        Integer cached = memo.get(taskId);
        if (cached != null) return cached;
        if (!visiting.add(taskId)) throw new PlanSynthesisException("计划存在循环依赖：" + taskId);
        PlanTask task = tasks.get(taskId);
        if (task == null) throw new PlanSynthesisException("计划引用不存在的任务：" + taskId);
        int result = 1;
        for (String dependency : task.dependencies()) {
            result = Math.max(result, depth(dependency, tasks, memo, visiting) + 1);
        }
        visiting.remove(taskId);
        memo.put(taskId, result);
        return result;
    }

    private static void ensureOriginalRequestNotForwarded(String originalRequest, List<PlanTask> tasks) {
        String raw = originalRequest.strip();
        for (PlanTask task : tasks) {
            for (ValueExpression expression : task.inputs().values()) {
                if (containsRawRequest(expression, raw)) {
                    throw new PlanSynthesisException("禁止把用户原始请求直接作为能力参数：" + task.id());
                }
            }
        }
    }

    private static boolean containsRawRequest(ValueExpression expression, String raw) {
        if (!(expression instanceof ValueExpression.Literal literal)) return false;
        return containsRawValue(literal.value(), raw);
    }

    private static boolean containsRawValue(Object value, String raw) {
        if (value instanceof String text) return text.strip().equals(raw);
        if (value instanceof Iterable<?> values) {
            for (Object item : values) if (containsRawValue(item, raw)) return true;
        }
        if (value instanceof Map<?, ?> values) {
            for (Object item : values.values()) if (containsRawValue(item, raw)) return true;
        }
        return false;
    }

    private record OutputBinding(String goalId, String taskId, String field) {}
}
