package com.example.agent.model.bo;

import java.util.List;

/** Exact catalog constraints that a provider result must not silently replace. */
public record MusicHardConstraints(
        String track,
        List<String> artists,
        String album
) {
    public MusicHardConstraints {
        artists = artists == null ? List.of() : List.copyOf(artists);
    }
}
