package com.example.agent.agent.contract;

import com.example.agent.model.bo.MusicPreferenceType;

/** A preference explicitly stated in the current turn; never inferred from model knowledge. */
public record MusicPreferenceChange(
        MusicPreferenceType type,
        String value,
        int polarity,
        boolean persistent
) {
    public MusicPreferenceChange {
        if (type == null) throw new IllegalArgumentException("偏好类型不能为空");
        value = value == null ? "" : value.strip();
        if (value.isEmpty()) throw new IllegalArgumentException("偏好内容不能为空");
        if (polarity != 1 && polarity != -1) throw new IllegalArgumentException("偏好方向只能是 1 或 -1");
    }
}
