package com.example.agent.agent.contract;

import java.util.Map;

/** Public, auditable evidence returned by a child agent. */
public record MusicTaskEvidence(
        String type,
        String provider,
        String resourceId,
        Map<String, Object> attributes
) {
    public MusicTaskEvidence {
        type = type == null ? "" : type.strip();
        provider = provider == null ? "" : provider.strip();
        resourceId = resourceId == null ? "" : resourceId.strip();
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
