package com.example.agent.orchestration.observability;

import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.evaluation.TaskEvaluation;
import com.example.agent.agent.planner.PlanValidationException;
import com.example.agent.config.PlannerOperationsProperties;
import com.example.agent.orchestration.replanning.ReplanRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Structured, redacted planner telemetry with local alert state for health checks and tests. */
@Component
public class PlannerObservability {
    private static final Logger log = LoggerFactory.getLogger(PlannerObservability.class);
    private final PlannerOperationsProperties properties;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Deque<PlannerOperationalEvent> events = new ArrayDeque<>();
    private final Deque<PlannerOperationalAlert> alerts = new ArrayDeque<>();
    private final Set<String> completedSideEffects = ConcurrentHashMap.newKeySet();

    @Autowired
    public PlannerObservability(PlannerOperationsProperties properties, ObjectMapper mapper) {
        this(properties, mapper, true);
    }

    private PlannerObservability(PlannerOperationsProperties properties, ObjectMapper mapper, boolean enabled) {
        this.properties = properties;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public static PlannerObservability noop() {
        return new PlannerObservability(new PlannerOperationsProperties(),
                new ObjectMapper().findAndRegisterModules(), false);
    }

    public void compiled(UserGoalGraph graph, CompiledPlan plan, String workflowId) {
        if (!enabled || graph == null || plan == null) return;
        emit(new PlannerOperationalEvent(null, null, PlannerEventType.GOAL_GRAPH, workflowId,
                graph.graphId().toString(), plan.planId().toString(), "", Map.of(
                "schemaVersion", graph.schemaVersion(),
                "requestSha256", sha256(graph.originalRequest()),
                "requestLength", graph.originalRequest().length(),
                "goalCount", graph.goals().size(),
                "relationCount", graph.relations().size(),
                "goals", graph.goals().stream().map(goal -> Map.of(
                        "id", goal.id(), "operation", goal.operation().name(),
                        "target", goal.targetType().name(), "missingSlots", goal.missingSlots(),
                        "requiresConfirmation", goal.requiresConfirmation())).toList())));

        List<Map<String, Object>> tasks = plan.tasks().stream().map(task -> {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("id", task.id());
            value.put("capabilityId", task.capabilityId());
            value.put("goalIds", task.goalIds());
            value.put("dependencies", task.dependencies());
            value.put("inputSources", inputSources(task));
            return Map.<String, Object>copyOf(value);
        }).toList();
        emit(new PlannerOperationalEvent(null, null, PlannerEventType.COMPILED_PLAN, workflowId,
                graph.graphId().toString(), plan.planId().toString(), "", Map.of(
                "taskCount", plan.tasks().size(), "stageCount", plan.executionStages().size(),
                "maxReplans", plan.maxReplans(), "tasks", tasks)));
        if (plan.tasks().size() > properties.getTaskCountAlertThreshold()) {
            alert(PlannerAlertType.ABNORMAL_TASK_COUNT, workflowId, plan.planId().toString(),
                    Map.of("taskCount", plan.tasks().size(),
                            "threshold", properties.getTaskCountAlertThreshold()));
        }
        if (containsRawRequest(plan, graph.originalRequest())) {
            alert(PlannerAlertType.RAW_REQUEST_FORWARDING, workflowId, plan.planId().toString(),
                    Map.of("requestSha256", sha256(graph.originalRequest())));
        }
    }

    public void planningRejected(UserGoalGraph graph, RuntimeException error, String workflowId) {
        if (!enabled) return;
        String message = error == null || error.getMessage() == null ? "" : error.getMessage();
        List<String> codes = error instanceof PlanValidationException validation
                ? validation.issues().stream().map(value -> value.code()).distinct().toList() : List.of();
        emit(new PlannerOperationalEvent(null, null, PlannerEventType.PLANNING_REJECTED, workflowId,
                graph == null ? "" : graph.graphId().toString(), "", "", Map.of(
                "errorType", error == null ? "UNKNOWN" : error.getClass().getSimpleName(),
                "codes", codes, "messageSha256", sha256(message))));
        if (codes.contains("RAW_REQUEST_FORWARDING") || message.contains("原始请求")) {
            alert(PlannerAlertType.RAW_REQUEST_FORWARDING, workflowId,
                    graph == null ? "" : graph.graphId().toString(), Map.of("codes", codes));
        }
        if (codes.contains("CYCLIC_DEPENDENCY") || message.contains("循环依赖")
                || message.contains("DAG_DEADLOCK")) {
            alert(PlannerAlertType.WORKFLOW_CYCLE, workflowId,
                    graph == null ? "" : graph.graphId().toString(), Map.of("codes", codes));
        }
    }

    public void taskStarted(UUID workflowId, PlanTask task, CapabilitySideEffect sideEffect,
                            int attempt, String idempotencyKey) {
        if (!enabled) return;
        String workflow = value(workflowId);
        if (sideEffect != CapabilitySideEffect.READ_ONLY && !idempotencyKey.isBlank()
                && completedSideEffects.contains(idempotencyKey)) {
            alert(PlannerAlertType.DUPLICATE_SIDE_EFFECT, workflow, task.id(), Map.of(
                    "capabilityId", task.capabilityId(), "idempotencyKeySha256", sha256(idempotencyKey)));
        }
        emit(new PlannerOperationalEvent(null, null, PlannerEventType.TASK_STARTED, workflow,
                "", "", task.id(), Map.of("capabilityId", task.capabilityId(),
                "attempt", attempt, "sideEffect", sideEffect.name(),
                "inputSources", inputSources(task))));
    }

    public void taskFinished(UUID workflowId, PlanTask task, CapabilitySideEffect sideEffect,
                             String idempotencyKey, long durationMillis, String status,
                             String errorCode, TaskEvaluation evaluation) {
        if (!enabled) return;
        String workflow = value(workflowId);
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("capabilityId", task.capabilityId());
        attributes.put("durationMillis", Math.max(0, durationMillis));
        attributes.put("status", status == null ? "" : status);
        attributes.put("errorCode", errorCode == null ? "" : errorCode);
        attributes.put("sideEffect", sideEffect.name());
        if (evaluation != null) attributes.put("evaluationDecision", evaluation.decision().name());
        emit(new PlannerOperationalEvent(null, null, PlannerEventType.TASK_FINISHED, workflow,
                "", "", task.id(), Map.copyOf(attributes)));
        if (evaluation != null) {
            emit(new PlannerOperationalEvent(null, null, PlannerEventType.TASK_EVALUATION, workflow,
                    "", "", task.id(), Map.of("decision", evaluation.decision().name(),
                    "findingCodes", evaluation.findings().stream().map(value -> value.code()).toList(),
                    "reasonSha256", sha256(evaluation.correction()))));
        }
        if (sideEffect != CapabilitySideEffect.READ_ONLY && "COMPLETED".equals(status)
                && !idempotencyKey.isBlank()) completedSideEffects.add(idempotencyKey);
    }

    public void replan(UUID workflowId, ReplanRecord record) {
        if (!enabled || record == null) return;
        emit(new PlannerOperationalEvent(null, null, PlannerEventType.REPLAN, value(workflowId),
                "", "", record.failedTaskId(), Map.of("attempt", record.attempt(),
                "errorCode", record.errorCode(), "outcome", record.outcome().name(),
                "replacedTaskIds", record.replacedTaskIds(), "preservedTaskCount",
                record.preservedTaskIds().size(), "reasonSha256", sha256(record.message()))));
    }

    public void deadlock(UUID workflowId, String subject) {
        if (enabled) alert(PlannerAlertType.WORKFLOW_CYCLE, value(workflowId), subject,
                Map.of("reason", "DAG_DEADLOCK"));
    }

    public void rollout(String workflowId, PlannerRolloutPolicy.Decision decision) {
        if (!enabled || decision == null) return;
        emit(new PlannerOperationalEvent(null, null, PlannerEventType.ROLLOUT_DECISION,
                workflowId, "", "", "", Map.of("action", decision.action().name(),
                "reason", decision.reason())));
    }

    public synchronized List<PlannerOperationalEvent> recentEvents() { return List.copyOf(events); }
    public synchronized List<PlannerOperationalAlert> recentAlerts() { return List.copyOf(alerts); }

    private void alert(PlannerAlertType type, String workflowId, String subject,
                       Map<String, Object> attributes) {
        PlannerOperationalAlert alert = new PlannerOperationalAlert(null, null, type,
                workflowId, subject, attributes);
        synchronized (this) {
            alerts.addFirst(alert);
            trim(alerts);
        }
        emit(new PlannerOperationalEvent(null, Instant.now(), PlannerEventType.ALERT,
                workflowId, "", "", subject, Map.of("alertType", type.name(),
                "attributes", attributes)));
        log.warn("planner_alert={}", json(alert));
    }

    private void emit(PlannerOperationalEvent event) {
        synchronized (this) {
            events.addFirst(event);
            trim(events);
        }
        log.info("planner_event={}", json(event));
    }

    private <T> void trim(Deque<T> values) {
        while (values.size() > properties.getEventHistoryLimit()) values.removeLast();
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ignored) { return "{\"serializationError\":true}"; }
    }

    private static Map<String, String> inputSources(PlanTask task) {
        LinkedHashMap<String, String> sources = new LinkedHashMap<>();
        task.inputs().forEach((field, expression) -> sources.put(field, source(expression)));
        return Map.copyOf(sources);
    }

    private static String source(ValueExpression value) {
        if (value instanceof ValueExpression.TaskOutput output) return "TASK_OUTPUT:" + output.taskId();
        return value.kind().name();
    }

    private static boolean containsRawRequest(CompiledPlan plan, String raw) {
        if (raw == null || raw.isBlank()) return false;
        return plan.tasks().stream().flatMap(task -> task.inputs().values().stream())
                .anyMatch(value -> value instanceof ValueExpression.Literal literal
                        && contains(literal.value(), raw.strip()));
    }

    private static boolean contains(Object value, String raw) {
        if (value instanceof String text) return text.strip().equals(raw);
        if (value instanceof Iterable<?> values) {
            for (Object item : values) if (contains(item, raw)) return true;
        }
        if (value instanceof Map<?, ?> values) {
            for (Object item : values.values()) if (contains(item, raw)) return true;
        }
        return false;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String value(UUID id) { return id == null ? "" : id.toString(); }
}
