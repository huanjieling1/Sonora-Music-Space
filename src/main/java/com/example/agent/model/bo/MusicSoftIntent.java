package com.example.agent.model.bo;

import java.util.List;

/** Subjective intent used for query expansion and ranking, never as an exact entity claim. */
public record MusicSoftIntent(
        String goal,
        List<String> avoid
) {
    public MusicSoftIntent {
        goal = goal == null ? "" : goal;
        avoid = avoid == null ? List.of() : List.copyOf(avoid);
    }
}
