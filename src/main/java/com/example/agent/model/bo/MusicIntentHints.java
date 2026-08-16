package com.example.agent.model.bo;

import java.util.List;

/** Optional discovery hints. They may improve ranking but cannot override hard constraints. */
public record MusicIntentHints(
        List<String> genres,
        List<String> moods,
        List<String> scenes,
        List<String> languages
) {
    public MusicIntentHints {
        genres = genres == null ? List.of() : List.copyOf(genres);
        moods = moods == null ? List.of() : List.copyOf(moods);
        scenes = scenes == null ? List.of() : List.copyOf(scenes);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }

    public MusicIntentHints(List<String> genres, List<String> moods, List<String> scenes) {
        this(genres, moods, scenes, List.of());
    }
}
