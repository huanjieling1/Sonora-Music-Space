package com.example.agent.model.vo.music;

import java.time.LocalDateTime;
import java.util.List;

public record MusicProfileSummaryVo(
        String stage,
        String stageLabel,
        String headline,
        String overview,
        String confidenceLabel,
        List<MusicProfileInsightVo> likes,
        List<MusicProfileInsightVo> avoids,
        List<String> observations,
        LocalDateTime generatedAt
) {
    public MusicProfileSummaryVo {
        likes = likes == null ? List.of() : List.copyOf(likes);
        avoids = avoids == null ? List.of() : List.copyOf(avoids);
        observations = observations == null ? List.of() : List.copyOf(observations);
    }
}
