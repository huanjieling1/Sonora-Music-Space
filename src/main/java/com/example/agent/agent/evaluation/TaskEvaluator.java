package com.example.agent.agent.evaluation;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.CapabilityFieldSchema;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedEntityReference;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.planner.SafeJsonPath;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Deterministic gate between a successful capability call and the DAG COMPLETED state. */
@Component
public final class TaskEvaluator {
    private final SafeJsonPath jsonPath;

    public TaskEvaluator(SafeJsonPath jsonPath) {
        this.jsonPath = jsonPath;
    }

    public TaskEvaluation evaluate(PlanTask task, AgentCapabilityDefinition capability,
                                   Map<String, Object> resolvedInputs, TypedTaskResult result) {
        if (task == null || capability == null) throw new IllegalArgumentException("任务验收上下文不能为空");
        Map<String, Object> inputs = resolvedInputs == null ? Map.of() : Map.copyOf(resolvedInputs);
        ArrayList<EvaluationFinding> findings = new ArrayList<>();

        checkInputs(task, capability, inputs, findings);
        if (result == null) {
            add(findings, "MISSING_TASK_RESULT", EvaluationDecision.REVISE, task.id(), "工具没有返回类型化结果");
            return finish(task.id(), findings);
        }
        if (!task.id().equals(result.taskId())) {
            add(findings, "TASK_ID_MISMATCH", EvaluationDecision.FAIL, result.taskId(), "返回结果属于其他任务");
        }
        if (!result.successful()) {
            EvaluationDecision decision = transientFailure(result.errorCode())
                    ? EvaluationDecision.REVISE : EvaluationDecision.FAIL;
            add(findings, "CAPABILITY_RESULT_FAILED", decision, result.errorCode(), result.errorMessage());
            return finish(task.id(), findings);
        }
        if (!capability.outputSchema().equals(result.outputSchema())) {
            add(findings, "OUTPUT_SCHEMA_MISMATCH", EvaluationDecision.REPLAN,
                    result.outputSchema().id(), "工具结果类型与能力 Output Schema 不一致");
        } else {
            checkOutputSchema(capability, result.output(), findings);
        }
        checkEvidence(capability, result, findings);
        checkEntities(capability, inputs, result, findings);
        checkCriteria(task, capability, inputs, result, findings);
        return finish(task.id(), findings);
    }

    private static void checkInputs(PlanTask task, AgentCapabilityDefinition capability,
                                    Map<String, Object> inputs, List<EvaluationFinding> findings) {
        capability.inputSchema().fields().forEach((name, field) -> {
            if (field.required() && (!inputs.containsKey(name) || inputs.get(name) == null)) {
                add(findings, "REQUIRED_INPUT_MISSING", EvaluationDecision.ASK_USER,
                        name, "任务缺少能力必填输入：" + name);
            }
        });
    }

    private static void checkOutputSchema(AgentCapabilityDefinition capability, Object output,
                                          List<EvaluationFinding> findings) {
        if (!(output instanceof Map<?, ?> object)) {
            add(findings, "OUTPUT_NOT_OBJECT", EvaluationDecision.REVISE, "$", "能力输出必须是 JSON 对象");
            return;
        }
        for (Map.Entry<String, CapabilityFieldSchema> entry : capability.outputSchema().fields().entrySet()) {
            Object value = object.get(entry.getKey());
            if (entry.getValue().required() && value == null) {
                add(findings, "OUTPUT_FIELD_MISSING", EvaluationDecision.REVISE,
                        "$." + entry.getKey(), "输出缺少必填字段");
            } else if (value != null && !matches(value, entry.getValue())) {
                add(findings, "OUTPUT_FIELD_TYPE_MISMATCH", EvaluationDecision.REVISE,
                        "$." + entry.getKey(), "输出字段类型与 Schema 不一致");
            }
        }
        if (!capability.outputSchema().additionalProperties()) {
            for (Object key : object.keySet()) {
                if (!capability.outputSchema().fields().containsKey(String.valueOf(key))) {
                    add(findings, "UNDECLARED_OUTPUT_FIELD", EvaluationDecision.REVISE,
                            "$." + key, "输出包含 Schema 未声明字段");
                }
            }
        }
    }

    private static void checkEvidence(AgentCapabilityDefinition capability, TypedTaskResult result,
                                      List<EvaluationFinding> findings) {
        if (result.evidenceIds().isEmpty()) {
            add(findings, "EVIDENCE_MISSING", EvaluationDecision.REVISE, result.taskId(), "结果没有可审计证据");
        }
        if (capability.evidencePolicy().providerRequired() && result.provider().isBlank()) {
            add(findings, "PROVIDER_MISSING", EvaluationDecision.REVISE, result.taskId(), "结果没有声明数据来源");
        }
        if (capability.evidencePolicy().resourceIdRequired() && result.resourceId().isBlank()) {
            add(findings, "RESOURCE_ID_MISSING", EvaluationDecision.REVISE, result.taskId(), "结果没有来源资源 ID");
        }
        if (result.output() instanceof Map<?, ?> output) {
            for (String field : List.of("provider", "source")) {
                if (output.containsKey(field) && (output.get(field) == null
                        || String.valueOf(output.get(field)).isBlank())) {
                    add(findings, "OUTPUT_SOURCE_MISSING", EvaluationDecision.REVISE,
                            "$." + field, "输出中的数据来源为空");
                }
            }
        }
    }

    private static void checkEntities(AgentCapabilityDefinition capability, Map<String, Object> inputs,
                                      TypedTaskResult result, List<EvaluationFinding> findings) {
        if (capability.evidencePolicy().entityMatchRequired() && result.entities().isEmpty()) {
            add(findings, "ENTITY_EVIDENCE_MISSING", EvaluationDecision.REVISE,
                    result.taskId(), "结果没有规范实体名称和 ID");
            return;
        }
        for (String field : List.of("artistName", "trackTitle")) {
            Object expected = inputs.get(field);
            if (!(expected instanceof String text) || text.isBlank() || result.entities().isEmpty()) continue;
            boolean matched = result.entities().stream().anyMatch(entity -> entityNames(entity)
                    .anyMatch(name -> normalized(name).equals(normalized(text))));
            if (!matched) {
                add(findings, "INPUT_OUTPUT_ENTITY_MISMATCH", EvaluationDecision.REPLAN,
                        field, "输入实体与返回实体不一致：" + text);
            }
        }
        for (String field : List.of("artist", "track")) {
            Object entityInput = inputs.get(field);
            if (!(entityInput instanceof Map<?, ?> map) || map.get("id") == null
                    || result.entities().isEmpty()) continue;
            String expectedId = String.valueOf(map.get("id"));
            String expectedProvider = map.get("provider") == null ? "" : String.valueOf(map.get("provider"));
            boolean matched = result.entities().stream().anyMatch(entity ->
                    entity.entityId().equals(expectedId)
                            && (expectedProvider.isBlank()
                            || normalized(entity.provider()).equals(normalized(expectedProvider))));
            if (!matched) {
                add(findings, "INPUT_OUTPUT_ENTITY_MISMATCH", EvaluationDecision.REPLAN,
                        field + ".id", "输入实体 ID 与返回实体不一致");
            }
        }
    }

    private static java.util.stream.Stream<String> entityNames(TypedEntityReference entity) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(entity.canonicalName()),
                entity.aliases().stream());
    }

    private void checkCriteria(PlanTask task, AgentCapabilityDefinition capability,
                               Map<String, Object> inputs, TypedTaskResult result,
                               List<EvaluationFinding> findings) {
        for (AcceptanceCriterion criterion : task.acceptanceCriteria()) {
            Object actual = subject(result.output(), criterion.subject());
            Object expected = literal(criterion.expected());
            boolean passed = switch (criterion.type()) {
                case OUTPUT_PRESENT -> present(actual);
                case OUTPUT_TYPE -> expected == null || typeName(actual).equalsIgnoreCase(String.valueOf(expected));
                case ENTITY_MATCH -> entityCriterion(actual, inputs, result.entities());
                case COUNT -> compareCount(actual, expected == null ? first(inputs, "limit", "count") : expected);
                case SOURCE -> sourceCriterion(actual, expected, result);
                case CONSTRAINT -> compare(actual, expected, criterion.attributes().getOrDefault("operator", "EQUALS"));
                case STATE_CHANGE -> stateChanged(result.output());
                case GOAL_COVERAGE -> truthy(actual) && (expected == null || Objects.equals(actual, expected));
                case CUSTOM -> false;
            };
            if (!passed && criterion.required()) {
                EvaluationDecision decision = switch (criterion.type()) {
                    case STATE_CHANGE -> EvaluationDecision.FAIL;
                    case GOAL_COVERAGE, ENTITY_MATCH, CONSTRAINT, CUSTOM -> EvaluationDecision.REPLAN;
                    default -> EvaluationDecision.REVISE;
                };
                add(findings, "ACCEPTANCE_" + criterion.type().name() + "_FAILED", decision,
                        criterion.id(), criterion.description().isBlank()
                                ? "任务未满足验收条件 " + criterion.id() : criterion.description());
            }
        }
        if (capability.sideEffect() != CapabilitySideEffect.READ_ONLY && !stateChanged(result.output())) {
            add(findings, "STATE_CHANGE_NOT_PROVEN", EvaluationDecision.FAIL,
                    task.id(), "接口返回成功，但没有证据证明副作用真实发生");
        }
    }

    private Object subject(Object output, String path) {
        if ("$".equals(path) || "$.result".equals(path)) return output;
        SafeJsonPath.JsonPathResult value = jsonPath.read(output, path);
        if (value.found()) return value.value();
        if (!(output instanceof Map<?, ?> map)) return null;
        return switch (path) {
            case "$.artist" -> first(map, "artist", "artistName", "canonicalName", "profile");
            case "$.queue" -> map.get("success");
            case "$.track" -> first(map, "track", "trackId");
            default -> null;
        };
    }

    private static Object first(Map<?, ?> values, String... keys) {
        for (String key : keys) if (values.get(key) != null) return values.get(key);
        return null;
    }

    private static boolean entityCriterion(Object actual, Map<String, Object> inputs,
                                           List<TypedEntityReference> entities) {
        if (!present(actual) || entities.isEmpty()) return false;
        Object expected = first(inputs, "artistName", "trackTitle");
        return expected == null || entities.stream().anyMatch(entity ->
                entityNames(entity).anyMatch(name ->
                        normalized(name).equals(normalized(String.valueOf(expected)))));
    }

    private static boolean sourceCriterion(Object actual, Object expected, TypedTaskResult result) {
        Object value = actual == null ? result.provider() : actual;
        return present(value) && (expected == null || normalized(String.valueOf(value))
                .equals(normalized(String.valueOf(expected))));
    }

    private static boolean compareCount(Object actual, Object expected) {
        if (expected == null) return present(actual);
        long actualCount;
        if (actual instanceof Collection<?> collection) actualCount = collection.size();
        else if (actual instanceof Map<?, ?> map) actualCount = map.size();
        else if (actual instanceof Number number) actualCount = number.longValue();
        else return false;
        if (!(expected instanceof Number number)) return false;
        return actualCount == number.longValue();
    }

    static boolean compare(Object actual, Object expected, String operator) {
        if ("EXISTS".equalsIgnoreCase(operator)) return present(actual);
        if (actual == null || expected == null) return false;
        return switch (operator.toUpperCase(Locale.ROOT)) {
            case "NOT_EQUALS" -> !Objects.equals(actual, expected);
            case "CONTAINS" -> normalized(String.valueOf(actual)).contains(normalized(String.valueOf(expected)));
            case "IN" -> expected instanceof Collection<?> values && values.contains(actual);
            case "NOT_IN" -> expected instanceof Collection<?> values && !values.contains(actual);
            case "MATCHES" -> String.valueOf(actual).matches(String.valueOf(expected));
            case "GREATER_THAN" -> number(actual) > number(expected);
            case "GREATER_THAN_OR_EQUAL" -> number(actual) >= number(expected);
            case "LESS_THAN" -> number(actual) < number(expected);
            case "LESS_THAN_OR_EQUAL" -> number(actual) <= number(expected);
            default -> Objects.equals(actual, expected)
                    || normalized(String.valueOf(actual)).equals(normalized(String.valueOf(expected)));
        };
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static Object literal(ValueExpression expression) {
        return expression instanceof ValueExpression.Literal literal ? literal.value() : null;
    }

    private static boolean stateChanged(Object output) {
        return output instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("success"));
    }

    private static boolean present(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof Collection<?> collection) return !collection.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0;
        return present(value);
    }

    private static String typeName(Object value) {
        if (value instanceof String) return ValueType.STRING.name();
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ValueType.INTEGER.name();
        }
        if (value instanceof Number) return ValueType.DECIMAL.name();
        if (value instanceof Boolean) return ValueType.BOOLEAN.name();
        if (value instanceof List<?>) return ValueType.ARRAY.name();
        if (value instanceof Map<?, ?>) return ValueType.OBJECT.name();
        return ValueType.ANY.name();
    }

    private static boolean matches(Object value, CapabilityFieldSchema field) {
        if (field.type() == ValueType.ANY) return true;
        if (field.type() == ValueType.ENTITY) return value instanceof Map<?, ?>;
        if (field.type() == ValueType.ARRAY) {
            if (!(value instanceof List<?> values)) return false;
            return field.itemType() == ValueType.ANY || values.stream().allMatch(item -> matches(item, field.itemType()));
        }
        return matches(value, field.type());
    }

    private static boolean matches(Object value, ValueType type) {
        return switch (type) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long;
            case DECIMAL -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case OBJECT, ENTITY -> value instanceof Map<?, ?>;
            case ARRAY -> value instanceof List<?>;
            case ANY -> true;
        };
    }

    private static boolean transientFailure(String code) {
        String value = code == null ? "" : code.toUpperCase(Locale.ROOT);
        return value.contains("TIMEOUT") || value.contains("TRANSIENT") || value.contains("UNAVAILABLE")
                || value.contains("RATE_LIMIT");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("[\\s._-]+", "");
    }

    private static void add(List<EvaluationFinding> findings, String code, EvaluationDecision decision,
                            String subject, String message) {
        findings.add(new EvaluationFinding(code, decision, subject, message));
    }

    private static TaskEvaluation finish(String taskId, List<EvaluationFinding> findings) {
        if (findings.isEmpty()) return TaskEvaluation.pass(taskId);
        EvaluationDecision decision = findings.stream().map(EvaluationFinding::decision)
                .reduce(EvaluationDecision.PASS, EvaluationDecision::combine);
        String correction = findings.stream().map(EvaluationFinding::message).filter(value -> !value.isBlank())
                .distinct().reduce((left, right) -> left + "；" + right).orElse("");
        String waitingSlot = decision == EvaluationDecision.ASK_USER
                ? findings.stream().filter(value -> value.decision() == EvaluationDecision.ASK_USER)
                .map(EvaluationFinding::subject).findFirst().orElse("evaluation.input") : "";
        return new TaskEvaluation(taskId, decision, findings, correction, waitingSlot);
    }
}
