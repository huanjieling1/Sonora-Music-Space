package com.example.agent.orchestration.observability;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlannerOperationalAlert(
        UUID alertId,
        Instant occurredAt,
        PlannerAlertType type,
        String workflowId,
        String subject,
        Map<String, Object> attributes
) {
    public PlannerOperationalAlert {
        alertId = alertId == null ? UUID.randomUUID() : alertId;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        if (type == null) throw new IllegalArgumentException("告警类型不能为空");
        workflowId = workflowId == null ? "" : workflowId.strip();
        subject = subject == null ? "" : subject.strip();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
