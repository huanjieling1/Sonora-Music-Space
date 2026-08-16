package com.example.agent.model.bo;

import java.util.List;
import java.util.UUID;

public record QqPublicPlaylistBo(
        String id,
        String name,
        String description,
        String coverUrl,
        String creatorName,
        String creatorAvatarUrl,
        long listenCount,
        int trackCount,
        List<String> tags,
        String externalUrl,
        UUID searchId,
        List<MusicTrackBo> tracks,
        String policyVersion,
        MusicPersonalizationStatus personalizationStatus
) {
    public QqPublicPlaylistBo {
        tags = tags == null ? List.of() : List.copyOf(tags);
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }
}

