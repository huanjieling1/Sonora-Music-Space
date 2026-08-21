package com.example.agent.agent.capability;

import java.util.Set;

/** Minimum auditable evidence required before a capability result may be accepted. */
public record CapabilityEvidencePolicy(
        Set<String> requiredTypes,
        boolean providerRequired,
        boolean resourceIdRequired,
        boolean entityMatchRequired,
        boolean stateChangeRequired
) {
    public CapabilityEvidencePolicy {
        requiredTypes = requiredTypes == null ? Set.of() : requiredTypes.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::strip)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (stateChangeRequired && requiredTypes.isEmpty()) {
            throw new IllegalArgumentException("状态变更能力必须声明至少一种证据类型");
        }
    }

    public static CapabilityEvidencePolicy none() {
        return new CapabilityEvidencePolicy(Set.of(), false, false, false, false);
    }

    public static CapabilityEvidencePolicy read(String type, boolean resourceId, boolean entityMatch) {
        return new CapabilityEvidencePolicy(Set.of(type), true, resourceId, entityMatch, false);
    }

    public static CapabilityEvidencePolicy mutation(String type) {
        return new CapabilityEvidencePolicy(Set.of(type), true, false, false, true);
    }
}
