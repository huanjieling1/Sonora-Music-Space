package com.example.agent.model.bo;

public record MusicSearchArtistBo(
        String id,
        String name,
        String imageUrl,
        String provider,
        int matchedTracks
) {
}
