package com.example.agent.model.dto.music;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MusicPlaylistTrackRequest(
        @NotNull(message = "推荐曝光不能为空") UUID searchId,
        @NotBlank(message = "歌曲不能为空") String trackId
) {
}
