package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicSearchGenreBo;

public record MusicSearchGenreVo(String id, String name, String description, String searchQuery) {
    public static MusicSearchGenreVo from(MusicSearchGenreBo genre) {
        return new MusicSearchGenreVo(genre.id(), genre.name(), genre.description(), genre.searchQuery());
    }
}
