package com.example.agent.model.dto.music;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QqMusicSessionRequest(
        @NotBlank(message = "请输入 QQ 音乐 Cookie")
        @Size(max = 16384, message = "QQ 音乐 Cookie 长度不正确")
        String cookie
) {
}
