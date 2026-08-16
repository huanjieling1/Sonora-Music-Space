package com.example.agent.model.bo;

public record MusicSearchTask(
        MusicSearchTaskType type,
        String query,
        String track,
        String artist,
        String album
) {
}
