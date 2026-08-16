package com.example.agent.model.dto.music;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MusicPlaylistOpenRequest(
        @NotNull(message = "会话不能为空") UUID conversationId
) {
}
