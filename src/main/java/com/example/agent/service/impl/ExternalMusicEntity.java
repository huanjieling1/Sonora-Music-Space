package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicEntityType;

import java.util.List;

public record ExternalMusicEntity(
        String canonicalName,
        MusicEntityType entityType,
        List<String> aliases,
        double confidence,
        String source,
        String sourceRef
) {
}
