package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilityConfirmationPolicy;
import com.example.agent.agent.capability.CapabilityFieldSchema;
import com.example.agent.agent.capability.CapabilityPrecondition;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.PlanDraft;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Static validator and security gate for model- or rule-produced plan drafts. */
@Component
public final class PlanValidator {
    private static final String ACCEPT_CAPABILITY = "planner.goal.accept";
    private final AgentCapabilityRegistry registry;

    public PlanValidator(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public PlanValidationResult validate(UserGoalGraph graph, PlanDraft draft,
                                         PlanValidationContext context) {
        ArrayList<PlanValidationIssue> issues = new ArrayList<>();
        if (graph == null || draft == null || context == null) {
            issue(issues, "MISSING_VALIDATION_INPUT", "", "", "目标图、计划草案和验证上下文均不能为空");
            return new PlanValidationResult(issues, 0, 0, 0);
        }
        if (!draft.goalGraphId().equals(graph.graphId())) {
            issue(issues, "GOAL_GRAPH_MISMATCH", "", "", "计划草案关联了不同的目标图");
        }

        Map<String, GoalNode> goals = indexGoals(graph.goals(), issues);
        Map<String, PlanTask> tasks = indexTasks(draft.tasks(), issues);
        validateTaskReferences(tasks, goals, issues);
        boolean cyclic = detectCycles(tasks, issues);

        LinkedHashMap<String, AgentCapabilityDefinition> capabilities = new LinkedHashMap<>();
        for (PlanTask task : draft.tasks()) {
            AgentCapabilityDefinition capability = registry.find(task.capabilityId())
                    .filter(AgentCapabilityDefinition::plannerVisible).orElse(null);
            if (capability == null) {
                issue(issues, "CAPABILITY_NOT_REGISTERED", task.id(), "",
                        "能力未注册或不可供 Planner 使用：" + task.capabilityId());
                continue;
            }
            if (registry.toolNames().contains(task.capabilityId())) {
                issue(issues, "TOOL_NAME_AS_CAPABILITY", task.id(), "",
                        "计划任务不能直接使用工具实现名称");
            }
            capabilities.put(task.id(), capability);
        }
        for (PlanTask task : draft.tasks()) {
            AgentCapabilityDefinition capability = capabilities.get(task.id());
            if (capability == null) continue;
            validateInputs(graph.originalRequest(), task, capability, tasks, capabilities, context, issues);
            validateRetries(task, capability, issues);
            validateSideEffects(task, capability, goals, context, issues);
        }

        if (!cyclic) {
            validateOutputReferenceAncestry(draft.tasks(), tasks, issues);
            validateRuntimePreconditions(draft.tasks(), tasks, capabilities, goals, context, issues);
        }
        validateGoalCoverage(graph.goals(), draft.tasks(), issues);

        Budget budget = budget(draft.tasks(), tasks, capabilities, cyclic, issues);
        if (budget.cost() > context.maxCostUnits()) {
            issue(issues, "COST_BUDGET_EXCEEDED", "", "",
                    "预计成本 " + budget.cost() + " 超过预算 " + context.maxCostUnits());
        }
        if (budget.duration() > context.maxDurationSeconds()) {
            issue(issues, "TIME_BUDGET_EXCEEDED", "", "",
                    "最坏耗时 " + budget.duration() + " 秒超过预算 " + context.maxDurationSeconds());
        }
        if (budget.attempts() > context.maxTotalAttempts()) {
            issue(issues, "RETRY_BUDGET_EXCEEDED", "", "",
                    "总尝试次数 " + budget.attempts() + " 超过预算 " + context.maxTotalAttempts());
        }
        return new PlanValidationResult(issues, budget.cost(), budget.duration(), budget.attempts());
    }

    public void validateOrThrow(UserGoalGraph graph, PlanDraft draft, PlanValidationContext context) {
        PlanValidationResult result = validate(graph, draft, context);
        if (!result.valid()) throw new PlanValidationException(result.issues());
    }

    private static Map<String, GoalNode> indexGoals(List<GoalNode> source,
                                                     List<PlanValidationIssue> issues) {
        LinkedHashMap<String, GoalNode> result = new LinkedHashMap<>();
        for (GoalNode goal : source) {
            if (result.putIfAbsent(goal.id(), goal) != null) {
                issue(issues, "DUPLICATE_GOAL_ID", "", goal.id(), "目标 ID 必须唯一");
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, PlanTask> indexTasks(List<PlanTask> source,
                                                     List<PlanValidationIssue> issues) {
        LinkedHashMap<String, PlanTask> result = new LinkedHashMap<>();
        for (PlanTask task : source) {
            if (result.putIfAbsent(task.id(), task) != null) {
                issue(issues, "DUPLICATE_TASK_ID", task.id(), "", "任务 ID 必须唯一");
            }
        }
        return Map.copyOf(result);
    }

    private static void validateTaskReferences(Map<String, PlanTask> tasks, Map<String, GoalNode> goals,
                                               List<PlanValidationIssue> issues) {
        for (PlanTask task : tasks.values()) {
            for (String dependency : task.dependencies()) {
                if (!tasks.containsKey(dependency)) {
                    issue(issues, "UNKNOWN_TASK_REFERENCE", task.id(), "",
                            "依赖任务不存在：" + dependency);
                }
                if (task.id().equals(dependency)) {
                    issue(issues, "SELF_DEPENDENCY", task.id(), "", "任务不能依赖自身");
                }
            }
            for (String goalId : task.goalIds()) {
                if (!goals.containsKey(goalId)) {
                    issue(issues, "UNKNOWN_GOAL_REFERENCE", task.id(), goalId,
                            "任务引用了不存在的用户目标");
                }
            }
        }
    }

    private static boolean detectCycles(Map<String, PlanTask> tasks, List<PlanValidationIssue> issues) {
        HashMap<String, Integer> states = new HashMap<>();
        for (String taskId : tasks.keySet()) {
            if (cycle(taskId, tasks, states)) {
                issue(issues, "CYCLIC_DEPENDENCY", taskId, "", "计划任务图存在循环依赖");
                return true;
            }
        }
        return false;
    }

    private static boolean cycle(String taskId, Map<String, PlanTask> tasks, Map<String, Integer> states) {
        int state = states.getOrDefault(taskId, 0);
        if (state == 1) return true;
        if (state == 2) return false;
        states.put(taskId, 1);
        PlanTask task = tasks.get(taskId);
        if (task != null) {
            for (String dependency : task.dependencies()) {
                if (tasks.containsKey(dependency) && cycle(dependency, tasks, states)) return true;
            }
        }
        states.put(taskId, 2);
        return false;
    }

    private static void validateInputs(String originalRequest, PlanTask task,
                                       AgentCapabilityDefinition capability,
                                       Map<String, PlanTask> tasks,
                                       Map<String, AgentCapabilityDefinition> knownCapabilities,
                                       PlanValidationContext context,
                                       List<PlanValidationIssue> issues) {
        Map<String, CapabilityFieldSchema> fields = capability.inputSchema().fields();
        for (Map.Entry<String, CapabilityFieldSchema> field : fields.entrySet()) {
            if (field.getValue().required() && !task.inputs().containsKey(field.getKey())) {
                issue(issues, "UNBOUND_REQUIRED_INPUT", task.id(), "",
                        "必填输入未绑定：" + field.getKey());
            }
        }
        if (!capability.inputSchema().additionalProperties()) {
            for (String input : task.inputs().keySet()) {
                if (!fields.containsKey(input)) {
                    issue(issues, "UNDECLARED_INPUT", task.id(), "", "能力未声明输入：" + input);
                }
            }
        }
        for (Map.Entry<String, ValueExpression> input : task.inputs().entrySet()) {
            ValueExpression expression = input.getValue();
            if (expression == null) {
                issue(issues, "NULL_INPUT_BINDING", task.id(), "", "输入绑定不能为空：" + input.getKey());
                continue;
            }
            CapabilityFieldSchema expected = fields.get(input.getKey());
            validateExpression(task, input.getKey(), expression, expected, tasks, knownCapabilities,
                    context, issues);
            if (containsRawRequest(expression, originalRequest.strip())) {
                issue(issues, "RAW_REQUEST_FORWARDING", task.id(), "",
                        "禁止把原始用户请求直接透传为能力参数：" + input.getKey());
            }
        }
        for (ValueExpression condition : task.activationConditions()) {
            if (condition == null) {
                issue(issues, "NULL_ACTIVATION_CONDITION", task.id(), "", "条件分支表达式不能为空");
            } else {
                validateExpression(task, "activationCondition", condition, null, tasks,
                        knownCapabilities, context, issues);
            }
        }
    }

    private static void validateExpression(PlanTask task, String inputName, ValueExpression expression,
                                           CapabilityFieldSchema expected, Map<String, PlanTask> tasks,
                                           Map<String, AgentCapabilityDefinition> capabilities,
                                           PlanValidationContext context,
                                           List<PlanValidationIssue> issues) {
        ValueType actual = expression.valueType();
        CapabilityFieldSchema actualField = null;
        if (expression instanceof ValueExpression.UserInput userInput && userInput.required()) {
            issue(issues, "UNRESOLVED_USER_INPUT", task.id(), "",
                    "计划进入执行前仍缺少用户输入：" + userInput.slot());
        } else if (expression instanceof ValueExpression.ProfileValue profileValue) {
            if (!context.authenticated() || context.principalId().isBlank()) {
                issue(issues, "PROFILE_ACCESS_WITHOUT_PRINCIPAL", task.id(), "",
                        "画像访问必须绑定当前登录用户");
            }
            if (!isCurrentUserProfilePath(profileValue.path())) {
                issue(issues, "CROSS_USER_PROFILE_ACCESS", task.id(), "",
                        "画像路径不属于当前用户只读命名空间：" + profileValue.path());
            }
            if (!context.profileAvailable()) {
                issue(issues, "PROFILE_NOT_AVAILABLE", task.id(), "", "当前用户画像不可用");
            }
        } else if (expression instanceof ValueExpression.TaskOutput output) {
            PlanTask producer = tasks.get(output.taskId());
            if (producer == null) {
                issue(issues, "UNKNOWN_TASK_OUTPUT", task.id(), "",
                        "TASK_OUTPUT 引用了不存在的任务：" + output.taskId());
            } else {
                AgentCapabilityDefinition producerCapability = capabilities.get(output.taskId());
                if (producerCapability != null) {
                    actualField = outputField(producerCapability, output.path());
                    if (actualField == null && !"$".equals(output.path())) {
                        issue(issues, "UNKNOWN_OUTPUT_PATH", task.id(), "",
                                "输出 Schema 不包含路径：" + output.path());
                    } else if (actualField != null) {
                        actual = actualField.type();
                    } else {
                        actual = ValueType.OBJECT;
                    }
                }
            }
        } else if (expression instanceof ValueExpression.Literal literal) {
            ValueType inferred = inferLiteralType(literal.value());
            if (!compatible(inferred, literal.valueType())) {
                issue(issues, "LITERAL_TYPE_MISMATCH", task.id(), "",
                        "字面量声明类型与实际值不一致：" + inputName);
            }
            actual = inferred;
        }
        if (expected != null && !compatible(actual, expected.type())) {
            issue(issues, "INPUT_TYPE_MISMATCH", task.id(), "",
                    "输入 " + inputName + " 类型 " + actual + " 不能赋给 " + expected.type());
        }
        if (expected != null && actualField != null) {
            if (expected.type() == ValueType.ARRAY && expected.itemType() != ValueType.ANY
                    && actualField.itemType() != ValueType.ANY
                    && !compatible(actualField.itemType(), expected.itemType())) {
                issue(issues, "ARRAY_ITEM_TYPE_MISMATCH", task.id(), "",
                        "数组元素类型不兼容：" + inputName);
            }
            if (expected.type() == ValueType.ENTITY
                    && expected.entityType() != com.example.agent.agent.contract.planning.GoalTargetType.NONE
                    && actualField.entityType() != com.example.agent.agent.contract.planning.GoalTargetType.NONE
                    && expected.entityType() != actualField.entityType()) {
                issue(issues, "ENTITY_TYPE_MISMATCH", task.id(), "",
                        "实体类型不兼容：" + inputName);
            }
        }
    }

    private static CapabilityFieldSchema outputField(AgentCapabilityDefinition capability, String path) {
        if ("$".equals(path)) return null;
        if (path == null || !path.matches("^\\$\\.[A-Za-z][A-Za-z0-9_]*$")) return null;
        return capability.outputSchema().fields().get(path.substring(2));
    }

    private static void validateRetries(PlanTask task, AgentCapabilityDefinition capability,
                                        List<PlanValidationIssue> issues) {
        if (task.maxAttempts() > capability.executionPolicy().maxAttempts()) {
            issue(issues, "RETRY_POLICY_EXCEEDED", task.id(), "",
                    "任务重试次数超过能力声明上限");
        }
    }

    private static void validateSideEffects(PlanTask task, AgentCapabilityDefinition capability,
                                            Map<String, GoalNode> goals, PlanValidationContext context,
                                            List<PlanValidationIssue> issues) {
        if (!context.allowedSideEffects().contains(capability.sideEffect())) {
            issue(issues, "SIDE_EFFECT_NOT_ALLOWED", task.id(), "",
                    "当前上下文不允许副作用级别 " + capability.sideEffect());
        }
        List<GoalNode> relatedGoals = task.goalIds().stream().map(goals::get)
                .filter(java.util.Objects::nonNull).toList();
        if (capability.confirmationPolicy() == CapabilityConfirmationPolicy.EXPLICIT_INTENT
                && relatedGoals.stream().anyMatch(goal -> !goal.requiresConfirmation())) {
            issue(issues, "EXPLICIT_INTENT_REQUIRED", task.id(), "",
                    "副作用能力缺少用户明确操作意图");
        }
        if (capability.confirmationPolicy() == CapabilityConfirmationPolicy.ALWAYS
                && relatedGoals.stream().anyMatch(goal -> !context.confirmedGoalIds().contains(goal.id()))) {
            issue(issues, "USER_CONFIRMATION_REQUIRED", task.id(), "",
                    "副作用能力需要用户确认");
        }
    }

    private static void validateOutputReferenceAncestry(List<PlanTask> source,
                                                        Map<String, PlanTask> tasks,
                                                        List<PlanValidationIssue> issues) {
        for (PlanTask task : source) {
            ArrayList<ValueExpression> expressions = new ArrayList<>(task.inputs().values());
            expressions.addAll(task.activationConditions());
            for (ValueExpression expression : expressions) {
                if (expression instanceof ValueExpression.TaskOutput output
                        && tasks.containsKey(output.taskId())
                        && !isAncestor(output.taskId(), task.id(), tasks, new HashSet<>())) {
                    issue(issues, "UNDECLARED_UPSTREAM_OUTPUT", task.id(), "",
                            "TASK_OUTPUT 只能读取已声明依赖链上的任务：" + output.taskId());
                }
            }
        }
    }

    private static boolean isAncestor(String candidate, String taskId, Map<String, PlanTask> tasks,
                                      Set<String> visited) {
        if (!visited.add(taskId)) return false;
        PlanTask task = tasks.get(taskId);
        if (task == null) return false;
        if (task.dependencies().contains(candidate)) return true;
        return task.dependencies().stream().anyMatch(value -> isAncestor(candidate, value, tasks, visited));
    }

    private static void validateRuntimePreconditions(List<PlanTask> tasks,
                                                     Map<String, PlanTask> taskIndex,
                                                     Map<String, AgentCapabilityDefinition> capabilities,
                                                     Map<String, GoalNode> goals,
                                                     PlanValidationContext context,
                                                     List<PlanValidationIssue> issues) {
        for (PlanTask task : tasks) {
            AgentCapabilityDefinition capability = capabilities.get(task.id());
            if (capability == null) continue;
            for (CapabilityPrecondition precondition : capability.preconditions()) {
                if (!precondition.required()) continue;
                boolean satisfied = switch (precondition.type()) {
                    case AUTHENTICATED_USER -> context.authenticated() && !context.principalId().isBlank();
                    case PROFILE_AVAILABLE -> context.profileAvailable();
                    case RECENT_SEARCH_RESULTS -> context.recentResultsAvailable()
                            || hasUpstreamOutput(task.id(), taskIndex, capabilities, Set.of("tracks", "searchId"));
                    case EXPLICIT_USER_INTENT -> task.goalIds().stream().map(goals::get)
                            .filter(java.util.Objects::nonNull).allMatch(GoalNode::requiresConfirmation);
                    default -> true;
                };
                if (!satisfied) {
                    issue(issues, "PRECONDITION_NOT_SATISFIED", task.id(), "",
                            "能力前置条件未满足：" + precondition.id());
                }
            }
        }
    }

    private static boolean hasUpstreamOutput(String taskId, Map<String, PlanTask> tasks,
                                             Map<String, AgentCapabilityDefinition> capabilities,
                                             Set<String> outputNames) {
        PlanTask task = tasks.get(taskId);
        if (task == null) return false;
        for (String dependency : task.dependencies()) {
            AgentCapabilityDefinition capability = capabilities.get(dependency);
            if (capability != null && capability.outputSchema().fields().keySet().stream()
                    .anyMatch(outputNames::contains)) return true;
            if (hasUpstreamOutput(dependency, tasks, capabilities, outputNames)) return true;
        }
        return false;
    }

    private static void validateGoalCoverage(List<GoalNode> goals, List<PlanTask> tasks,
                                             List<PlanValidationIssue> issues) {
        for (GoalNode goal : goals) {
            List<PlanTask> implementation = tasks.stream()
                    .filter(task -> task.goalIds().contains(goal.id()))
                    .filter(task -> !task.capabilityId().equals(ACCEPT_CAPABILITY)).toList();
            if (implementation.isEmpty()) {
                issue(issues, "GOAL_NOT_IMPLEMENTED", "", goal.id(), "用户目标没有实现任务");
            }
            boolean hasCriteria = !goal.acceptanceCriteria().isEmpty()
                    || implementation.stream().anyMatch(task -> !task.acceptanceCriteria().isEmpty());
            if (!hasCriteria) {
                issue(issues, "GOAL_WITHOUT_ACCEPTANCE_CRITERIA", "", goal.id(),
                        "用户目标没有验收标准");
            }
            boolean hasAcceptanceTask = tasks.stream().anyMatch(task -> task.goalIds().contains(goal.id())
                    && task.capabilityId().equals(ACCEPT_CAPABILITY));
            if (!hasAcceptanceTask) {
                issue(issues, "GOAL_WITHOUT_ACCEPTANCE_TASK", "", goal.id(),
                        "用户目标没有独立验收任务");
            }
        }
    }

    private static Budget budget(List<PlanTask> source, Map<String, PlanTask> tasks,
                                 Map<String, AgentCapabilityDefinition> capabilities,
                                 boolean cyclic, List<PlanValidationIssue> issues) {
        int cost = 0;
        int attempts = 0;
        for (PlanTask task : source) {
            attempts += task.maxAttempts();
            AgentCapabilityDefinition capability = capabilities.get(task.id());
            if (capability != null) {
                cost += capability.executionPolicy().estimatedCostUnits() * task.maxAttempts();
            }
        }
        int duration = 0;
        if (!cyclic) {
            HashMap<String, Integer> memo = new HashMap<>();
            for (PlanTask task : source) {
                duration = Math.max(duration, duration(task.id(), tasks, capabilities, memo, new HashSet<>()));
            }
        }
        return new Budget(cost, duration, attempts);
    }

    private static int duration(String taskId, Map<String, PlanTask> tasks,
                                Map<String, AgentCapabilityDefinition> capabilities,
                                Map<String, Integer> memo, Set<String> visiting) {
        Integer cached = memo.get(taskId);
        if (cached != null) return cached;
        if (!visiting.add(taskId)) return 0;
        PlanTask task = tasks.get(taskId);
        if (task == null) return 0;
        int upstream = 0;
        for (String dependency : task.dependencies()) {
            upstream = Math.max(upstream, duration(dependency, tasks, capabilities, memo, visiting));
        }
        AgentCapabilityDefinition capability = capabilities.get(taskId);
        int own = capability == null ? 0
                : capability.executionPolicy().timeoutSeconds() * task.maxAttempts();
        int result = upstream + own;
        visiting.remove(taskId);
        memo.put(taskId, result);
        return result;
    }

    private static boolean isCurrentUserProfilePath(String path) {
        return path.equals("$.musicProfile") || path.startsWith("$.musicProfile.")
                || path.equals("$.currentUser.profile") || path.startsWith("$.currentUser.profile.");
    }

    private static ValueType inferLiteralType(Object value) {
        if (value == null) return ValueType.ANY;
        if (value instanceof String) return ValueType.STRING;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ValueType.INTEGER;
        }
        if (value instanceof Number) return ValueType.DECIMAL;
        if (value instanceof Boolean) return ValueType.BOOLEAN;
        if (value instanceof Map<?, ?>) return ValueType.OBJECT;
        if (value instanceof Iterable<?>) return ValueType.ARRAY;
        return ValueType.ANY;
    }

    private static boolean compatible(ValueType actual, ValueType expected) {
        if (expected == null || expected == ValueType.ANY) return true;
        if (actual == ValueType.ANY) return false;
        return actual == expected || (actual == ValueType.INTEGER && expected == ValueType.DECIMAL);
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

    private static void issue(List<PlanValidationIssue> issues, String code, String taskId,
                              String goalId, String message) {
        issues.add(new PlanValidationIssue(code, taskId, goalId, message));
    }

    private record Budget(int cost, int duration, int attempts) {}
}
