package com.example.agent.model.bo;

public record QqMusicQrLoginBo(
        String loginId,
        String loginMode,
        String status,
        String message,
        String qrImage,
        String expiresAt,
        QqMusicStatusBo connection
) {
}
