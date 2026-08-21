package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilityFieldSchema;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Workflow-scoped, thread-safe result store enforcing schema and declared-upstream boundaries. */
public final class TaskResultStore {
    private final Map<String, PlanTask> tasks;
    private final Map<String, AgentCapabilityDefinition> capabilities;
    private final ConcurrentHashMap<String, TypedTaskResult> results = new ConcurrentHashMap<>();

    public TaskResultStore(CompiledPlan plan, AgentCapabilityRegistry registry) {
        if (plan == null || registry == null) throw new IllegalArgumentException("计划和能力注册表不能为空");
        LinkedHashMap<String, PlanTask> taskIndex = new LinkedHashMap<>();
        LinkedHashMap<String, AgentCapabilityDefinition> capabilityIndex = new LinkedHashMap<>();
        for (PlanTask task : plan.tasks()) {
            taskIndex.put(task.id(), task);
            AgentCapabilityDefinition capability = registry.find(task.capabilityId())
                    .filter(AgentCapabilityDefinition::plannerVisible)
                    .orElseThrow(() -> new IllegalArgumentException("计划能力未注册：" + task.capabilityId()));
            capabilityIndex.put(task.id(), capability);
        }
        this.tasks = Map.copyOf(taskIndex);
        this.capabilities = Map.copyOf(capabilityIndex);
    }

    public void store(TypedTaskResult result) {
        if (result == null) throw new IllegalArgumentException("任务结果不能为空");
        PlanTask task = tasks.get(result.taskId());
        if (task == null) throw new IllegalArgumentException("结果不属于当前计划：" + result.taskId());
        AgentCapabilityDefinition capability = capabilities.get(task.id());
        if (!capability.outputSchema().equals(result.outputSchema())) {
            throw new IllegalArgumentException("结果 Output Schema 与能力声明不一致：" + result.taskId());
        }
        if (result.successful()) {
            validateOutput(result.output(), capability, result.taskId());
            if (capability.evidencePolicy().providerRequired() && result.provider().isBlank()) {
                throw new IllegalArgumentException("结果缺少 provider：" + result.taskId());
            }
            if (capability.evidencePolicy().resourceIdRequired() && result.resourceId().isBlank()) {
                throw new IllegalArgumentException("结果缺少 resourceId：" + result.taskId());
            }
            if (capability.evidencePolicy().entityMatchRequired() && result.entities().isEmpty()) {
                throw new IllegalArgumentException("结果缺少规范实体名称和 ID：" + result.taskId());
            }
        }
        if (results.putIfAbsent(result.taskId(), result) != null) {
            throw new IllegalStateException("任务结果已经写入，禁止覆盖：" + result.taskId());
        }
    }

    public Optional<TypedTaskResult> find(String taskId) {
        return Optional.ofNullable(results.get(taskId));
    }

    public Map<String, TypedTaskResult> snapshot() {
        return Map.copyOf(results);
    }

    public ReferenceResolution lookupFor(String consumerTaskId, String producerTaskId) {
        if (!tasks.containsKey(consumerTaskId)) {
            return failure("UNKNOWN_CONSUMER_TASK", ValueExpression.Kind.TASK_OUTPUT, consumerTaskId,
                    "消费任务不属于当前计划");
        }
        if (!tasks.containsKey(producerTaskId)) {
            return failure("UNKNOWN_PRODUCER_TASK", ValueExpression.Kind.TASK_OUTPUT, producerTaskId,
                    "上游任务不属于当前计划");
        }
        if (!isAncestor(producerTaskId, consumerTaskId, new HashSet<>())) {
            return failure("UNDECLARED_UPSTREAM_ACCESS", ValueExpression.Kind.TASK_OUTPUT, producerTaskId,
                    "任务只能读取 CompiledPlan 中声明的上游结果");
        }
        TypedTaskResult result = results.get(producerTaskId);
        if (result == null) {
            return failure("TASK_RESULT_NOT_AVAILABLE", ValueExpression.Kind.TASK_OUTPUT, producerTaskId,
                    "上游任务结果尚不存在");
        }
        if (!result.successful()) {
            return failure("UPSTREAM_TASK_FAILED", ValueExpression.Kind.TASK_OUTPUT, producerTaskId,
                    "上游任务失败：" + result.errorCode());
        }
        return ReferenceResolution.success(result, ValueType.OBJECT);
    }

    private boolean isAncestor(String candidate, String consumer, Set<String> visited) {
        if (!visited.add(consumer)) return false;
        PlanTask task = tasks.get(consumer);
        if (task == null) return false;
        if (task.dependencies().contains(candidate)) return true;
        for (String dependency : task.dependencies()) {
            if (isAncestor(candidate, dependency, visited)) return true;
        }
        return false;
    }

    private static void validateOutput(Object output, AgentCapabilityDefinition capability, String taskId) {
        if (!(output instanceof Map<?, ?> object)) {
            throw new IllegalArgumentException("能力输出必须是 JSON 对象：" + taskId);
        }
        Map<String, CapabilityFieldSchema> fields = capability.outputSchema().fields();
        for (Map.Entry<String, CapabilityFieldSchema> field : fields.entrySet()) {
            if (field.getValue().required() && (!object.containsKey(field.getKey())
                    || object.get(field.getKey()) == null)) {
                throw new IllegalArgumentException("能力输出缺少必填字段：" + field.getKey());
            }
        }
        if (!capability.outputSchema().additionalProperties()) {
            for (Object key : object.keySet()) {
                if (!fields.containsKey(String.valueOf(key))) {
                    throw new IllegalArgumentException("能力输出包含未声明字段：" + key);
                }
            }
        }
        for (Map.Entry<String, CapabilityFieldSchema> field : fields.entrySet()) {
            Object value = object.get(field.getKey());
            if (value == null) continue;
            if (!matches(value, field.getValue())) {
                throw new IllegalArgumentException("能力输出字段类型不匹配：" + field.getKey());
            }
        }
    }

    private static boolean matches(Object value, CapabilityFieldSchema field) {
        if (field.type() == ValueType.ANY) return true;
        if (field.type() == ValueType.ENTITY) return value instanceof Map<?, ?>;
        if (field.type() == ValueType.ARRAY) {
            if (!(value instanceof List<?> values)) return false;
            if (field.itemType() == ValueType.ANY) return true;
            return values.stream().allMatch(item -> matchesType(item, field.itemType()));
        }
        return matchesType(value, field.type());
    }

    private static boolean matchesType(Object value, ValueType type) {
        return switch (type) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case DECIMAL -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case OBJECT, ENTITY -> value instanceof Map<?, ?>;
            case ARRAY -> value instanceof List<?>;
            case ANY -> true;
        };
    }

    private static ReferenceResolution failure(String code, ValueExpression.Kind kind,
                                               String reference, String message) {
        return ReferenceResolution.failure(new ReferenceResolutionError(code, kind, reference, message));
    }
}
