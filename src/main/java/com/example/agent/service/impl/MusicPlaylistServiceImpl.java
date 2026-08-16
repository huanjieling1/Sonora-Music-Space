package com.example.agent.service.impl;

import com.example.agent.config.MusicPersonalizationProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicPlaylistBo;
import com.example.agent.model.bo.MusicPlaylistDetailBo;
import com.example.agent.service.MusicPlaylistService;
import com.example.agent.service.MusicRecommendationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MusicPlaylistServiceImpl implements MusicPlaylistService {
    private final MusicPlaylistRepository playlists;
    private final MusicPersonalizationRepository personalization;
    private final MusicRecommendationService recommendations;
    private final MusicPersonalizationProperties properties;
    private final MusicEmbeddingClient embeddings;
    private final Neo4jMusicGraphClient graph;

    public MusicPlaylistServiceImpl(MusicPlaylistRepository playlists,
                                    MusicPersonalizationRepository personalization,
                                    MusicRecommendationService recommendations,
                                    MusicPersonalizationProperties properties,
                                    MusicEmbeddingClient embeddings,
                                    Neo4jMusicGraphClient graph) {
        this.playlists = playlists;
        this.personalization = personalization;
        this.recommendations = recommendations;
        this.properties = properties;
        this.embeddings = embeddings;
        this.graph = graph;
    }

    @Override
    public List<MusicPlaylistBo> list(long userId) {
        return playlists.list(userId);
    }

    @Override
    public MusicPlaylistBo create(long userId, String name, String description) {
        return playlists.create(userId, name, description);
    }

    @Override
    public MusicPlaylistBo createFromExposure(long userId, UUID exposureId,
                                              String name, String description) {
        return playlists.createFromExposure(userId, exposureId, name, description);
    }

    @Override
    public MusicPlaylistBo createRecommended(long userId, UUID conversationId,
                                             String name, String description) {
        var recommendation = recommendations.recommend(
                new MusicRecommendationAo(userId, conversationId, description.strip(), 1, 10));
        return playlists.createFromExposure(userId, recommendation.searchId(), name, description);
    }

    @Override
    public MusicPlaylistDetailBo open(long userId, UUID playlistId, UUID conversationId) {
        personalization.requireOwnedConversation(userId, conversationId);
        MusicPlaylistBo playlist = playlists.requireOwned(userId, playlistId);
        var storedTracks = playlists.tracks(userId, playlistId);
        UUID exposureId = storedTracks.isEmpty() ? null : UUID.randomUUID();
        String policyVersion = personalization.policy(properties.activePolicyVersion())
                .map(MusicPersonalizationRepository.PolicyRow::version).orElse("baseline-v1");
        MusicPersonalizationStatus status = personalizationStatus(userId);
        var exposureTracks = storedTracks.stream().map(item -> {
            var track = item.track();
            List<String> reasons = track.reasonCodes().isEmpty()
                    ? List.of("PLAYLIST_TRACK") : track.reasonCodes();
            return new MusicPersonalizationRepository.ExposureTrack(track,
                    Map.of("playlist", item.position()),
                    Map.of("playlistId", playlistId.toString(), "playlistPosition", item.position()),
                    reasons, List.of(), Math.max(0.01, 1.0 - item.position() * 0.001),
                    track.exploration());
        }).toList();
        if (exposureId != null) {
            personalization.recordExposure(userId, conversationId, exposureId,
                    "打开歌单：" + playlist.name(),
                    Map.of("intent", "PLAYLIST", "playlistId", playlistId.toString()),
                    policyVersion, status, exposureTracks);
        }
        return new MusicPlaylistDetailBo(playlists.requireOwned(userId, playlistId), exposureId,
                policyVersion, status, storedTracks);
    }

    @Override
    public MusicPlaylistBo update(long userId, UUID playlistId, String name, String description) {
        return playlists.update(userId, playlistId, name, description);
    }

    @Override
    public void delete(long userId, UUID playlistId) {
        playlists.delete(userId, playlistId);
    }

    @Override
    public MusicPlaylistBo addTrack(long userId, UUID playlistId, UUID exposureId, String trackId) {
        var item = personalization.findOwnedExposureItem(userId, exposureId, trackId)
                .orElseThrow(() -> new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "该歌曲不属于当前用户的这次推荐曝光"));
        return playlists.addTrack(userId, playlistId, item);
    }

    @Override
    public MusicPlaylistBo removeTrack(long userId, UUID playlistId, long playlistTrackId) {
        return playlists.removeTrack(userId, playlistId, playlistTrackId);
    }

    private MusicPersonalizationStatus personalizationStatus(long userId) {
        if (!properties.enabled()) return MusicPersonalizationStatus.DISABLED;
        if (!graph.ready() || (properties.embedding().enabled() && !embeddings.configured())) {
            return MusicPersonalizationStatus.DEGRADED;
        }
        var stats = personalization.profileStats(userId);
        return stats.labeledEvents() >= 3 && stats.exposures() >= 2
                ? MusicPersonalizationStatus.ACTIVE : MusicPersonalizationStatus.COLD_START;
    }
}
