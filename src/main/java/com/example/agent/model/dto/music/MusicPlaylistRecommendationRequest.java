package com.example.agent.model.dto.music;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MusicPlaylistRecommendationRequest(
        @NotNull(message = "会话不能为空") UUID conversationId,
        @NotBlank(message = "请描述想听的音乐")
        @Size(max = 500, message = "音乐描述不能超过 500 个字符") String description,
        @NotBlank(message = "歌单名称不能为空")
        @Size(max = 120, message = "歌单名称不能超过 120 个字符") String name
) {
}
