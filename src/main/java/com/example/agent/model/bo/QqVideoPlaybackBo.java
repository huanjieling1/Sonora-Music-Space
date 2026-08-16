package com.example.agent.model.bo;

public record QqVideoPlaybackBo(
        String id,
        String playbackUrl,
        long durationMs,
        String quality,
        String externalUrl
) {
}
