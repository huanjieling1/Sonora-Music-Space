package com.example.agent.orchestration.observability;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Value-only structured event safe for JSON log shipping. */
public record PlannerOperationalEvent(
        UUID eventId,
        Instant occurredAt,
        PlannerEventType type,
        String workflowId,
        String graphId,
        String planId,
        String taskId,
        Map<String, Object> attributes
) {
    public PlannerOperationalEvent {
        eventId = eventId == null ? UUID.randomUUID() : eventId;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        if (type == null) throw new IllegalArgumentException("观测事件类型不能为空");
        workflowId = text(workflowId);
        graphId = text(graphId);
        planId = text(planId);
        taskId = text(taskId);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    private static String text(String value) { return value == null ? "" : value.strip(); }
}
