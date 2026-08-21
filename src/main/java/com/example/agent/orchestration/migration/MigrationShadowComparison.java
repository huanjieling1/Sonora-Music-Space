package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentRoute;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Auditable comparison between the legacy outcome and its dynamic-plan contract projection. */
public record MigrationShadowComparison(
        UUID id,
        Instant createdAt,
        MusicAgentRoute legacyRoute,
        UUID goalGraphId,
        UUID compiledPlanId,
        List<String> dynamicCapabilities,
        boolean legacySuccessful,
        boolean compatible,
        List<String> findings
) {
    public MigrationShadowComparison {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        dynamicCapabilities = dynamicCapabilities == null ? List.of() : List.copyOf(dynamicCapabilities);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
