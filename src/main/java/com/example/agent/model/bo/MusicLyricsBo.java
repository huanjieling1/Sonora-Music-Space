package com.example.agent.model.bo;

import java.util.List;

public record MusicLyricsBo(
        String provider,
        String trackId,
        boolean available,
        boolean synced,
        List<MusicLyricLineBo> lines,
        String source,
        String message
) {
    public MusicLyricsBo {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public static MusicLyricsBo unavailable(String provider, String trackId, String message) {
        return new MusicLyricsBo(provider, trackId, false, false, List.of(), provider, message);
    }
}
