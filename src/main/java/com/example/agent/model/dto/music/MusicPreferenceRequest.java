package com.example.agent.model.dto.music;

import com.example.agent.model.bo.MusicPreferenceType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MusicPreferenceRequest(
        @NotNull MusicPreferenceType type,
        @NotBlank @Size(max = 200) String value,
        int polarity
) {
    @AssertTrue(message = "偏好方向只能是 1 或 -1")
    public boolean isPolarityValid() {
        return polarity == 1 || polarity == -1;
    }
}
