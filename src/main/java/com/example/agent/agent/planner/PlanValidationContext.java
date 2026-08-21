package com.example.agent.agent.planner;

import com.example.agent.agent.capability.CapabilitySideEffect;

import java.util.Set;

/** User/security state and hard budgets applied to one compilation attempt. */
public record PlanValidationContext(
        String principalId,
        boolean authenticated,
        boolean profileAvailable,
        boolean recentResultsAvailable,
        Set<String> confirmedGoalIds,
        Set<CapabilitySideEffect> allowedSideEffects,
        int maxCostUnits,
        int maxDurationSeconds,
        int maxTotalAttempts
) {
    public PlanValidationContext {
        principalId = principalId == null ? "" : principalId.strip();
        confirmedGoalIds = confirmedGoalIds == null ? Set.of() : Set.copyOf(confirmedGoalIds);
        allowedSideEffects = allowedSideEffects == null
                ? Set.of(CapabilitySideEffect.READ_ONLY) : Set.copyOf(allowedSideEffects);
        if (maxCostUnits < 0 || maxDurationSeconds < 1 || maxTotalAttempts < 1) {
            throw new IllegalArgumentException("计划验证预算必须为有效正数");
        }
    }

    public static PlanValidationContext standard(String principalId) {
        return new PlanValidationContext(principalId, true, true, false, Set.of(),
                Set.of(CapabilitySideEffect.READ_ONLY, CapabilitySideEffect.REVERSIBLE_SESSION,
                        CapabilitySideEffect.PERSISTENT_MUTATION),
                200, 300, 50);
    }
}
