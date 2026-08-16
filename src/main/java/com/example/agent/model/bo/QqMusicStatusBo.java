package com.example.agent.model.bo;

public record QqMusicStatusBo(
        boolean enabled,
        boolean bridgeAvailable,
        boolean sessionConfigured,
        String maskedAccount,
        String message
) {
}
