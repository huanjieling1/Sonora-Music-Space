package com.example.agent.model.vo.music;

import com.example.agent.model.bo.QqMusicStatusBo;

public record QqMusicStatusVo(
        boolean enabled,
        boolean bridgeAvailable,
        boolean sessionConfigured,
        String maskedAccount,
        String message
) {
    public static QqMusicStatusVo from(QqMusicStatusBo status) {
        return new QqMusicStatusVo(status.enabled(), status.bridgeAvailable(), status.sessionConfigured(),
                status.maskedAccount(), status.message());
    }
}
