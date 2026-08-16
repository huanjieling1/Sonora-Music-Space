package com.example.agent.model.vo.music;

import java.time.LocalDateTime;
import java.util.UUID;

public record MusicPreferenceVo(
        UUID id,
        String layer,
        String scopeType,
        String type,
        String value,
        int polarity,
        double confidence,
        int evidenceCount,
        String source,
        LocalDateTime expiresAt
) {
}
