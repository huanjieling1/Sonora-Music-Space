package com.example.agent.service;

import com.example.agent.model.bo.MusicPlaylistBo;
import com.example.agent.model.bo.MusicPlaylistDetailBo;

import java.util.List;
import java.util.UUID;

public interface MusicPlaylistService {
    List<MusicPlaylistBo> list(long userId);

    MusicPlaylistBo create(long userId, String name, String description);

    MusicPlaylistBo createFromExposure(long userId, UUID exposureId, String name, String description);

    MusicPlaylistBo createRecommended(long userId, UUID conversationId, String name, String description);

    MusicPlaylistDetailBo open(long userId, UUID playlistId, UUID conversationId);

    MusicPlaylistBo update(long userId, UUID playlistId, String name, String description);

    void delete(long userId, UUID playlistId);

    MusicPlaylistBo addTrack(long userId, UUID playlistId, UUID exposureId, String trackId);

    MusicPlaylistBo removeTrack(long userId, UUID playlistId, long playlistTrackId);
}
