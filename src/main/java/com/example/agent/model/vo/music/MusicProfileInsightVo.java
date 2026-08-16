package com.example.agent.model.vo.music;

public record MusicProfileInsightVo(
        String type,
        String typeLabel,
        String value,
        int polarity,
        String layer,
        double confidence,
        int evidenceCount,
        String basis
) {
}
