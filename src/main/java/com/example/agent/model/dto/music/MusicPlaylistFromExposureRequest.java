package com.example.agent.model.dto.music;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MusicPlaylistFromExposureRequest(
        @NotNull(message = "推荐曝光不能为空") UUID searchId,
        @NotBlank(message = "歌单名称不能为空")
        @Size(max = 120, message = "歌单名称不能超过 120 个字符") String name,
        @Size(max = 500, message = "歌单简介不能超过 500 个字符") String description
) {
}
