package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicSearchArtistBo;

public record MusicSearchArtistVo(String id, String name, String imageUrl, String provider, int matchedTracks) {
    public static MusicSearchArtistVo from(MusicSearchArtistBo artist) {
        return new MusicSearchArtistVo(artist.id(), artist.name(), artist.imageUrl(),
                artist.provider(), artist.matchedTracks());
    }
}
