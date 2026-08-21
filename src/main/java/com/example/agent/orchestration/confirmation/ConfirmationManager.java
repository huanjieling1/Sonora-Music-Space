package com.example.agent.orchestration.confirmation;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.CapabilityConfirmationPolicy;
import com.example.agent.agent.capability.CapabilitySideEffect;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Creates and resolves expiring, single-task confirmation requests. */
@Component
public final class ConfirmationManager {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    private final Clock clock;
    private final Duration ttl;

    public ConfirmationManager() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    public ConfirmationManager(Clock clock, Duration ttl) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.ttl = ttl == null ? DEFAULT_TTL : ttl;
        if (this.ttl.isZero() || this.ttl.isNegative()) throw new IllegalArgumentException("确认有效期必须大于零");
    }

    public boolean required(AgentCapabilityDefinition capability) {
        return capability != null && capability.sideEffect() != CapabilitySideEffect.READ_ONLY
                && capability.confirmationPolicy() != CapabilityConfirmationPolicy.NEVER;
    }

    public ConfirmationRequest create(UUID workflowId, String principalId, String taskId,
                                      AgentCapabilityDefinition capability, Map<String, Object> inputs,
                                      String idempotencyKey) {
        Instant now = clock.instant();
        String prompt = "确认执行“" + capability.name() + "”吗？该操作将修改当前状态。";
        return new ConfirmationRequest(UUID.randomUUID(), workflowId, principalId, taskId,
                capability.id(), capability.confirmationPolicy(), inputs, idempotencyKey, prompt,
                ConfirmationRequest.Status.PENDING, now, now.plus(ttl), null);
    }

    public ConfirmationRequest respond(ConfirmationRequest request, String principalId, Object reply) {
        return respond(request, principalId, reply, clock.instant());
    }

    public ConfirmationRequest respond(ConfirmationRequest request, String principalId,
                                       Object reply, Instant now) {
        if (request == null) throw new IllegalArgumentException("确认请求不能为空");
        if (principalId == null || !request.principalId().equals(principalId.strip())) {
            throw new SecurityException("确认请求不属于当前用户");
        }
        Instant effectiveNow = now == null ? clock.instant() : now;
        if (request.status() != ConfirmationRequest.Status.PENDING) return request;
        if (!effectiveNow.isBefore(request.expiresAt())) {
            return request.withStatus(ConfirmationRequest.Status.EXPIRED, effectiveNow);
        }
        Boolean approved = decision(reply);
        if (approved == null) throw new IllegalArgumentException("确认回复必须明确批准或拒绝");
        return request.withStatus(approved ? ConfirmationRequest.Status.APPROVED
                : ConfirmationRequest.Status.REJECTED, effectiveNow);
    }

    public ConfirmationRequest expireIfNeeded(ConfirmationRequest request) {
        if (request == null || (request.status() != ConfirmationRequest.Status.PENDING
                && request.status() != ConfirmationRequest.Status.APPROVED)
                || clock.instant().isBefore(request.expiresAt())) return request;
        return request.withStatus(ConfirmationRequest.Status.EXPIRED, clock.instant());
    }

    public boolean authorized(ConfirmationRequest request, Map<String, Object> currentInputs,
                              String idempotencyKey) {
        if (request == null || request.status() != ConfirmationRequest.Status.APPROVED) return false;
        if (!clock.instant().isBefore(request.expiresAt())) return false;
        return request.pendingInputs().equals(currentInputs == null ? Map.of() : currentInputs)
                && request.idempotencyKey().equals(idempotencyKey == null ? "" : idempotencyKey);
    }

    private static Boolean decision(Object reply) {
        if (reply instanceof Boolean value) return value;
        if (reply == null) return null;
        String value = String.valueOf(reply).strip().toLowerCase(Locale.ROOT);
        if (java.util.Set.of("approve", "approved", "confirm", "yes", "true", "同意", "确认", "批准")
                .contains(value)) return true;
        if (java.util.Set.of("reject", "rejected", "cancel", "no", "false", "拒绝", "取消")
                .contains(value)) return false;
        return null;
    }
}
