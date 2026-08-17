package com.example.agent.model.vo.music;

import com.example.agent.model.bo.QqMusicQrLoginBo;

public record QqMusicQrLoginVo(
        String loginId,
        String loginMode,
        String status,
        String message,
        String qrImage,
        String expiresAt,
        QqMusicStatusVo connection
) {
    public static QqMusicQrLoginVo from(QqMusicQrLoginBo login) {
        return new QqMusicQrLoginVo(login.loginId(), login.loginMode(), login.status(), login.message(), login.qrImage(),
                login.expiresAt(), login.connection() == null ? null : QqMusicStatusVo.from(login.connection()));
    }
}
