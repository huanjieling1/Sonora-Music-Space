package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicPlaylistBo;
import com.example.agent.model.bo.MusicPlaylistTrackBo;
import com.example.agent.model.bo.MusicPlaylistType;
import com.example.agent.model.bo.MusicTrackBo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MusicPlaylistRepository {
    private static final String FAVORITES_KEY = "favorites";
    private static final String RECENT_KEY = "recent";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public MusicPlaylistRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<MusicPlaylistBo> list(long userId) {
        ensureSystemPlaylists(userId);
        syncFavorites(userId);
        syncRecent(userId);
        return jdbc.query("""
                SELECT p.id, p.playlist_type, p.name, p.description,
                       COALESCE(p.cover_url, (
                           SELECT JSON_UNQUOTE(JSON_EXTRACT(t.track_snapshot, '$.imageUrl'))
                             FROM music_playlist_track t
                            WHERE t.playlist_id = p.id ORDER BY t.position LIMIT 1
                       )) AS display_cover,
                       (SELECT COUNT(*) FROM music_playlist_track t WHERE t.playlist_id = p.id) AS track_count,
                       p.editable, p.updated_at
                  FROM music_playlist p
                 WHERE p.user_id = ?
                 ORDER BY FIELD(p.playlist_type, 'FAVORITES', 'RECENT', 'RECOMMENDED', 'CUSTOM'),
                          p.updated_at DESC
                """, this::mapPlaylist, userId);
    }

    @Transactional
    public MusicPlaylistBo create(long userId, String name, String description) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO music_playlist
                    (id, user_id, playlist_type, name, description, editable)
                VALUES (?, ?, 'CUSTOM', ?, ?, 1)
                """, id.toString(), userId, clean(name), nullable(description));
        return requireOwned(userId, id);
    }

    @Transactional
    public MusicPlaylistBo createFromExposure(long userId, UUID exposureId,
                                              String name, String description) {
        List<ExposureHeader> headers = jdbc.query("""
                SELECT description, policy_version
                  FROM music_recommendation_exposure
                 WHERE id = ? AND user_id = ?
                """, (rs, row) -> new ExposureHeader(rs.getString("description"),
                rs.getString("policy_version")), exposureId.toString(), userId);
        if (headers.isEmpty()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "推荐曝光不存在或不属于当前用户");
        }
        List<StoredTrack> tracks = jdbc.query("""
                SELECT track_key, track_snapshot
                  FROM music_recommendation_item
                 WHERE exposure_id = ? ORDER BY display_position
                """, (rs, row) -> new StoredTrack(rs.getString("track_key"),
                rs.getString("track_snapshot")), exposureId.toString());
        if (tracks.isEmpty()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "这次推荐没有可保存的歌曲");
        }

        UUID id = UUID.randomUUID();
        MusicTrackBo first = readTrack(tracks.get(0).snapshot());
        ExposureHeader header = headers.get(0);
        jdbc.update("""
                INSERT INTO music_playlist
                    (id, user_id, playlist_type, name, description, cover_url,
                     source_description, source_exposure_id, policy_version, editable)
                VALUES (?, ?, 'RECOMMENDED', ?, ?, ?, ?, ?, ?, 1)
                """, id.toString(), userId, clean(name), nullable(description), nullable(first.imageUrl()),
                header.description(), exposureId.toString(), header.policyVersion());
        insertTracks(id, tracks);
        return requireOwned(userId, id);
    }

    @Transactional
    public MusicPlaylistBo update(long userId, UUID playlistId, String name, String description) {
        requireEditable(userId, playlistId);
        jdbc.update("""
                UPDATE music_playlist
                   SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP(6)
                 WHERE id = ? AND user_id = ?
                """, clean(name), nullable(description), playlistId.toString(), userId);
        return requireOwned(userId, playlistId);
    }

    @Transactional
    public void delete(long userId, UUID playlistId) {
        requireEditable(userId, playlistId);
        jdbc.update("DELETE FROM music_playlist WHERE id = ? AND user_id = ?",
                playlistId.toString(), userId);
    }

    @Transactional
    public MusicPlaylistBo addTrack(long userId, UUID playlistId,
                                    MusicPersonalizationRepository.ExposureItem item) {
        requireEditable(userId, playlistId);
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(position), 0) + 1
                  FROM music_playlist_track WHERE playlist_id = ?
                """, Integer.class, playlistId.toString());
        jdbc.update("""
                INSERT INTO music_playlist_track
                    (playlist_id, track_key, position, track_snapshot)
                VALUES (?, ?, ?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE track_snapshot = VALUES(track_snapshot)
                """, playlistId.toString(), item.trackKey(), next == null ? 1 : next,
                json(item.track()));
        jdbc.update("""
                UPDATE music_playlist
                   SET cover_url = COALESCE(cover_url, ?), updated_at = CURRENT_TIMESTAMP(6)
                 WHERE id = ? AND user_id = ?
                """, nullable(item.track().imageUrl()), playlistId.toString(), userId);
        return requireOwned(userId, playlistId);
    }

    @Transactional
    public MusicPlaylistBo removeTrack(long userId, UUID playlistId, long playlistTrackId) {
        requireEditable(userId, playlistId);
        int deleted = jdbc.update("""
                DELETE FROM music_playlist_track WHERE id = ? AND playlist_id = ?
                """, playlistTrackId, playlistId.toString());
        if (deleted == 0) throw new AppException(HttpStatus.NOT_FOUND, "歌单中没有这首歌曲");
        resequence(playlistId);
        jdbc.update("UPDATE music_playlist SET updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                playlistId.toString());
        return requireOwned(userId, playlistId);
    }

    @Transactional
    public MusicPlaylistBo requireOwned(long userId, UUID playlistId) {
        MusicPlaylistBo playlist = jdbc.query("""
                SELECT p.id, p.playlist_type, p.name, p.description,
                       COALESCE(p.cover_url, (
                           SELECT JSON_UNQUOTE(JSON_EXTRACT(t.track_snapshot, '$.imageUrl'))
                             FROM music_playlist_track t
                            WHERE t.playlist_id = p.id ORDER BY t.position LIMIT 1
                       )) AS display_cover,
                       (SELECT COUNT(*) FROM music_playlist_track t WHERE t.playlist_id = p.id) AS track_count,
                       p.editable, p.updated_at
                  FROM music_playlist p WHERE p.id = ? AND p.user_id = ?
                """, this::mapPlaylist, playlistId.toString(), userId).stream().findFirst().orElse(null);
        if (playlist == null) throw new AppException(HttpStatus.NOT_FOUND, "歌单不存在或不属于当前用户");
        return playlist;
    }

    @Transactional
    public List<MusicPlaylistTrackBo> tracks(long userId, UUID playlistId) {
        MusicPlaylistBo playlist = requireOwned(userId, playlistId);
        if (playlist.type() == MusicPlaylistType.FAVORITES) syncFavorites(userId);
        if (playlist.type() == MusicPlaylistType.RECENT) syncRecent(userId);
        return jdbc.query("""
                SELECT id, position, track_snapshot
                  FROM music_playlist_track WHERE playlist_id = ? ORDER BY position
                """, (rs, row) -> new MusicPlaylistTrackBo(rs.getLong("id"), rs.getInt("position"),
                readTrack(rs.getString("track_snapshot"))), playlistId.toString());
    }

    private void requireEditable(long userId, UUID playlistId) {
        MusicPlaylistBo playlist = requireOwned(userId, playlistId);
        if (!playlist.editable()) {
            throw new AppException(HttpStatus.CONFLICT, "系统歌单会随播放和收藏自动更新，不能手动修改");
        }
    }

    private void ensureSystemPlaylists(long userId) {
        insertSystemPlaylist(userId, FAVORITES_KEY, MusicPlaylistType.FAVORITES,
                "我喜欢的音乐", "点击喜欢的歌曲会自动出现在这里");
        insertSystemPlaylist(userId, RECENT_KEY, MusicPlaylistType.RECENT,
                "最近播放", "最近真正开始播放过的歌曲");
    }

    private void insertSystemPlaylist(long userId, String key, MusicPlaylistType type,
                                      String name, String description) {
        jdbc.update("""
                INSERT IGNORE INTO music_playlist
                    (id, user_id, playlist_type, system_key, name, description, editable)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """, UUID.randomUUID().toString(), userId, type.name(), key, name, description);
    }

    private void syncFavorites(long userId) {
        UUID playlistId = systemPlaylistId(userId, FAVORITES_KEY);
        List<StoredTrack> tracks = jdbc.query("""
                SELECT p.normalized_value AS track_key,
                       COALESCE((
                           SELECT i.track_snapshot
                             FROM music_recommendation_item i
                             JOIN music_recommendation_exposure e ON e.id = i.exposure_id
                            WHERE i.track_key = p.normalized_value AND e.user_id = p.user_id
                            ORDER BY e.created_at DESC LIMIT 1
                       ), c.metadata_json) AS track_snapshot
                  FROM music_preference_memory p
                  JOIN music_catalog_track c ON c.track_key = p.normalized_value
                 WHERE p.user_id = ? AND p.layer = 'L1' AND p.preference_type = 'TRACK'
                   AND p.polarity = 1 AND p.source = 'LIKE' AND p.deleted_at IS NULL
                 ORDER BY p.updated_at DESC LIMIT 200
                """, (rs, row) -> new StoredTrack(rs.getString("track_key"),
                rs.getString("track_snapshot")), userId);
        replaceSystemTracks(playlistId, distinct(tracks));
    }

    private void syncRecent(long userId) {
        UUID playlistId = systemPlaylistId(userId, RECENT_KEY);
        List<StoredTrack> raw = jdbc.query("""
                SELECT i.track_key, i.track_snapshot
                  FROM music_behavior_event b
                  JOIN music_recommendation_item i ON i.id = b.recommendation_item_id
                 WHERE b.user_id = ? AND b.event_type IN ('PLAY_START', 'COMPLETE', 'REPEAT')
                 ORDER BY b.created_at DESC LIMIT 500
                """, (rs, row) -> new StoredTrack(rs.getString("track_key"),
                rs.getString("track_snapshot")), userId);
        replaceSystemTracks(playlistId, distinct(raw).stream().limit(100).toList());
    }

    private UUID systemPlaylistId(long userId, String key) {
        String id = jdbc.queryForObject("""
                SELECT id FROM music_playlist WHERE user_id = ? AND system_key = ?
                """, String.class, userId, key);
        return UUID.fromString(id);
    }

    private void replaceSystemTracks(UUID playlistId, List<StoredTrack> tracks) {
        jdbc.update("DELETE FROM music_playlist_track WHERE playlist_id = ?", playlistId.toString());
        insertTracks(playlistId, tracks);
        String cover = tracks.isEmpty() ? null : readTrack(tracks.get(0).snapshot()).imageUrl();
        jdbc.update("""
                UPDATE music_playlist SET cover_url = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?
                """, nullable(cover), playlistId.toString());
    }

    private void insertTracks(UUID playlistId, List<StoredTrack> tracks) {
        int position = 0;
        for (StoredTrack track : tracks) {
            jdbc.update("""
                    INSERT INTO music_playlist_track
                        (playlist_id, track_key, position, track_snapshot)
                    VALUES (?, ?, ?, CAST(? AS JSON))
                    """, playlistId.toString(), track.trackKey(), ++position, track.snapshot());
        }
    }

    private void resequence(UUID playlistId) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM music_playlist_track WHERE playlist_id = ? ORDER BY position, id
                """, Long.class, playlistId.toString());
        int position = 0;
        for (Long id : ids) {
            jdbc.update("UPDATE music_playlist_track SET position = ? WHERE id = ?", ++position, id);
        }
    }

    private List<StoredTrack> distinct(List<StoredTrack> tracks) {
        Map<String, StoredTrack> unique = new LinkedHashMap<>();
        tracks.forEach(track -> unique.putIfAbsent(track.trackKey(), track));
        return new ArrayList<>(unique.values());
    }

    private MusicPlaylistBo mapPlaylist(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Timestamp updated = rs.getTimestamp("updated_at");
        return new MusicPlaylistBo(UUID.fromString(rs.getString("id")),
                MusicPlaylistType.valueOf(rs.getString("playlist_type")), rs.getString("name"),
                rs.getString("description"), rs.getString("display_cover"),
                rs.getInt("track_count"), rs.getBoolean("editable"),
                updated == null ? LocalDateTime.now() : updated.toLocalDateTime());
    }

    private MusicTrackBo readTrack(String value) {
        try {
            return objectMapper.readValue(value, MusicTrackBo.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored playlist track", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Playlist track cannot be serialized", exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record StoredTrack(String trackKey, String snapshot) {
    }

    private record ExposureHeader(String description, String policyVersion) {
    }
}
