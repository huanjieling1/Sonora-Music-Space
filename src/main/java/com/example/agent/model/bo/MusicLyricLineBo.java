package com.example.agent.model.bo;

public record MusicLyricLineBo(
        Long timeMs,
        String text,
        String translation,
        String romanization
) {
}
