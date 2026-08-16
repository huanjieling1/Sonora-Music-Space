package com.example.agent.model.bo;

import java.time.LocalDateTime;
import java.util.UUID;

public record MusicPlaylistBo(
        UUID id,
        MusicPlaylistType type,
        String name,
        String description,
        String coverUrl,
        int trackCount,
        boolean editable,
        LocalDateTime updatedAt
) {
}
