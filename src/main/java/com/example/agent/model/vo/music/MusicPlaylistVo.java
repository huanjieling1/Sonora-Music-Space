package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicPlaylistBo;
import com.example.agent.model.bo.MusicPlaylistType;

import java.time.LocalDateTime;
import java.util.UUID;

public record MusicPlaylistVo(
        UUID id,
        MusicPlaylistType type,
        String name,
        String description,
        String coverUrl,
        int trackCount,
        boolean editable,
        LocalDateTime updatedAt
) {
    public static MusicPlaylistVo from(MusicPlaylistBo playlist) {
        return new MusicPlaylistVo(playlist.id(), playlist.type(), playlist.name(), playlist.description(),
                playlist.coverUrl(), playlist.trackCount(), playlist.editable(), playlist.updatedAt());
    }
}
