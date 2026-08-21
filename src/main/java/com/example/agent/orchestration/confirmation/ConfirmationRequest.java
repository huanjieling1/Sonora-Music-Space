package com.example.agent.orchestration.confirmation;

import com.example.agent.agent.capability.CapabilityConfirmationPolicy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Persisted, principal-bound authorization for exactly one set of side-effect inputs. */
public record ConfirmationRequest(
        UUID requestId,
        UUID workflowId,
        String principalId,
        String taskId,
        String capabilityId,
        CapabilityConfirmationPolicy policy,
        Map<String, Object> pendingInputs,
        String idempotencyKey,
        String prompt,
        Status status,
        Instant createdAt,
        Instant expiresAt,
        Instant respondedAt
) {
    public ConfirmationRequest {
        if (requestId == null || workflowId == null) throw new IllegalArgumentException("确认请求标识不能为空");
        if (principalId == null || principalId.isBlank()) throw new IllegalArgumentException("确认请求必须绑定用户");
        if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("确认请求必须绑定任务");
        if (capabilityId == null || capabilityId.isBlank()) throw new IllegalArgumentException("确认请求必须绑定能力");
        principalId = principalId.strip();
        taskId = taskId.strip();
        capabilityId = capabilityId.strip();
        policy = policy == null ? CapabilityConfirmationPolicy.ALWAYS : policy;
        pendingInputs = pendingInputs == null ? Map.of() : Map.copyOf(pendingInputs);
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.strip();
        prompt = prompt == null ? "" : prompt.strip();
        status = status == null ? Status.PENDING : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        expiresAt = expiresAt == null ? createdAt : expiresAt;
        if (expiresAt.isBefore(createdAt)) throw new IllegalArgumentException("确认过期时间不能早于创建时间");
    }

    public enum Status { PENDING, APPROVED, REJECTED, EXPIRED }

    public String replySlot() {
        return "confirmation." + requestId;
    }

    public ConfirmationRequest withStatus(Status newStatus, Instant time) {
        return new ConfirmationRequest(requestId, workflowId, principalId, taskId, capabilityId,
                policy, pendingInputs, idempotencyKey, prompt, newStatus, createdAt, expiresAt, time);
    }
}
