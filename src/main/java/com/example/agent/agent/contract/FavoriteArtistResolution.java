package com.example.agent.agent.contract;

import java.util.List;

/** Auditable entity binding between a listener profile and a downstream artist lookup. */
public record FavoriteArtistResolution(
        boolean resolved,
        String artistName,
        String basis,
        double confidence,
        List<String> evidenceIds,
        String clarification
) {
    public FavoriteArtistResolution {
        artistName = artistName == null ? "" : artistName.strip();
        basis = basis == null ? "" : basis.strip();
        confidence = Double.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0;
        evidenceIds = evidenceIds == null ? List.of() : evidenceIds.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::strip).distinct().toList();
        clarification = clarification == null ? "" : clarification.strip();
        if (resolved && artistName.isBlank()) {
            throw new IllegalArgumentException("已解析的歌手实体不能为空");
        }
    }

    public static FavoriteArtistResolution unresolved(String clarification) {
        return new FavoriteArtistResolution(false, "", "", 0, List.of(), clarification);
    }
}
