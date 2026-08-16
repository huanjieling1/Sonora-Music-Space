package com.example.agent.model.bo;

import java.util.List;

public record MusicSearchPlan(
        MusicSearchIntent intent,
        String track,
        List<String> artists,
        String album,
        List<String> genres,
        List<String> moods,
        List<String> scenes,
        List<MusicSearchTask> tasks,
        double confidence,
        String clarificationQuestion
) {
}
