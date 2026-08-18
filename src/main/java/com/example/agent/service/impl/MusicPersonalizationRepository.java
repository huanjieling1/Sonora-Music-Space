package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicBehaviorEventType;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.MusicTrackBo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
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
import java.util.Optional;
import java.util.UUID;

@Repository
public class MusicPersonalizationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final MusicEmbeddingClient embeddings;

    public MusicPersonalizationRepository(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                          MusicEmbeddingClient embeddings) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.embeddings = embeddings;
    }

    public void requireOwnedConversation(long userId, UUID conversationId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM agent_conversation
                 WHERE id = ? AND user_id = ? AND is_deleted = 0
                """, Integer.class, conversationId.toString(), userId);
        if (count == null || count == 0) {
            throw new AppException(HttpStatus.NOT_FOUND, "会话不存在或不属于当前用户");
        }
    }

    @Transactional
    public void recordExposure(long userId, UUID conversationId, UUID exposureId,
                               String description, Object plan, String policyVersion,
                               MusicPersonalizationStatus status, List<ExposureTrack> tracks) {
        String fingerprint = MusicTrackIdentity.sha256(MusicTextNormalizer.normalize(description));
        recordExposure(userId, conversationId, exposureId, description, plan, policyVersion, status,
                fingerprint, nextBatchSequence(userId, conversationId, fingerprint), "STANDARD", tracks);
    }

    @Transactional
    public void recordExposure(long userId, UUID conversationId, UUID exposureId,
                               String description, Object plan, String policyVersion,
                               MusicPersonalizationStatus status, String requestFingerprint,
                               int batchSequence, String refreshSource, List<ExposureTrack> tracks) {
        requireOwnedConversation(userId, conversationId);
        jdbc.update("""
                INSERT INTO music_recommendation_exposure
                    (id, user_id, conversation_id, description, plan_json, policy_version,
                     personalization_status, request_fingerprint, batch_sequence, refresh_source)
                VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?, ?, ?, ?)
                """, exposureId.toString(), userId, conversationId.toString(), description,
                json(plan), policyVersion, status.name(), requestFingerprint,
                Math.max(1, batchSequence), truncate(refreshSource, 24));

        int position = 0;
        for (ExposureTrack item : tracks) {
            position++;
            MusicTrackBo track = item.track();
            String trackKey = MusicTrackIdentity.key(track);
            String contentText = MusicTrackIdentity.contentText(track, item.tags());
            String contentHash = MusicTrackIdentity.sha256(contentText);
            TrackProjectionState previous = projectionState(trackKey).orElse(null);
            jdbc.update("""
                    INSERT INTO music_catalog_track
                        (track_key, provider, provider_track_id, title, primary_artist, album,
                         content_text, content_hash, metadata_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                    ON DUPLICATE KEY UPDATE title = VALUES(title), primary_artist = VALUES(primary_artist),
                        album = VALUES(album), content_text = VALUES(content_text),
                        content_hash = VALUES(content_hash), metadata_json = VALUES(metadata_json),
                        last_seen_at = CURRENT_TIMESTAMP(6)
                    """, trackKey, track.provider(), track.id(), track.name(), primaryArtist(track), track.album(),
                    contentText, contentHash, json(track));
            upsertExposureTags(trackKey, item);
            Map<String, Object> featureSnapshot = new LinkedHashMap<>(item.features());
            featureSnapshot.put("tags", item.tags());
            jdbc.update("""
                    INSERT INTO music_recommendation_item
                        (exposure_id, track_key, provider, provider_track_id, display_position,
                         track_snapshot, source_ranks, feature_snapshot, final_score, reason_codes, exploration)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?,
                            CAST(? AS JSON), ?)
                    """, exposureId.toString(), trackKey, track.provider(), track.id(), position,
                    json(track), json(item.sourceRanks()), json(featureSnapshot), item.finalScore(),
                    json(item.reasonCodes()), item.exploration());
            boolean graphStale = previous == null || !contentHash.equals(previous.graphProjectedHash());
            boolean embeddingStale = embeddings.configured()
                    && (previous == null || !contentHash.equals(previous.embeddingContentHash()));
            if (graphStale || embeddingStale) {
                enqueueLatestSong(userId, trackKey, Map.of(
                        "trackKey", trackKey,
                        "track", track,
                        "tags", item.tags(),
                        "contentText", contentText,
                        "contentHash", contentHash));
            }
        }
    }

    public int nextBatchSequence(long userId, UUID conversationId, String requestFingerprint) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(batch_sequence), 0) + 1
                  FROM music_recommendation_exposure
                 WHERE user_id = ? AND conversation_id = ? AND request_fingerprint = ?
                """, Integer.class, userId, conversationId.toString(), requestFingerprint);
        return value == null ? 1 : Math.max(1, value);
    }

    public List<RecentExposureTrack> recentExposureTracks(long userId, UUID conversationId, int batchLimit) {
        if (conversationId == null || batchLimit <= 0) return List.of();
        List<String> exposureIds = jdbc.query("""
                SELECT id FROM music_recommendation_exposure
                 WHERE user_id = ? AND conversation_id = ?
                   AND created_at >= CURRENT_TIMESTAMP(6) - INTERVAL 24 HOUR
                 ORDER BY created_at DESC
                 LIMIT ?
                """, (rs, row) -> rs.getString("id"), userId, conversationId.toString(), batchLimit);
        if (exposureIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(exposureIds.size(), "?"));
        String sql = """
                SELECT e.id AS exposure_id, i.track_key, c.title, c.primary_artist
                  FROM music_recommendation_item i
                  JOIN music_recommendation_exposure e ON e.id = i.exposure_id
                  JOIN music_catalog_track c ON c.track_key = i.track_key
                 WHERE i.exposure_id IN (%s)
                 ORDER BY e.created_at DESC, i.display_position
                """.formatted(placeholders);
        return jdbc.query(sql, (rs, row) -> new RecentExposureTrack(
                rs.getString("exposure_id"), rs.getString("track_key"),
                rs.getString("title"), rs.getString("primary_artist")), exposureIds.toArray());
    }

    public Optional<ExposureItem> findOwnedExposureItem(long userId, UUID exposureId, String trackId) {
        List<ExposureItem> rows = jdbc.query("""
                SELECT i.id, i.track_key, i.provider, i.provider_track_id, i.track_snapshot,
                       i.feature_snapshot, i.display_position, e.conversation_id, e.policy_version
                  FROM music_recommendation_item i
                  JOIN music_recommendation_exposure e ON e.id = i.exposure_id
                 WHERE e.id = ? AND e.user_id = ? AND i.provider_track_id = ?
                 LIMIT 1
                """, (rs, row) -> new ExposureItem(
                rs.getLong("id"), rs.getString("track_key"), rs.getString("provider"),
                rs.getString("provider_track_id"), readTrack(rs.getString("track_snapshot")),
                readMap(rs.getString("feature_snapshot")), rs.getInt("display_position"),
                UUID.fromString(rs.getString("conversation_id")), rs.getString("policy_version")),
                exposureId.toString(), userId, trackId);
        return rows.stream().findFirst();
    }

    public void requireOwnedExposure(long userId, UUID exposureId, UUID conversationId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_recommendation_exposure
                 WHERE id = ? AND user_id = ? AND conversation_id = ?
                """, Integer.class, exposureId.toString(), userId, conversationId.toString());
        if (count == null || count == 0) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "反馈引用的推荐曝光不存在或不属于当前用户会话");
        }
    }

    @Transactional
    public EventWriteResult recordEvent(long userId, UUID eventId, UUID playbackSessionId, UUID exposureId,
                                        ExposureItem item, MusicBehaviorEventType type,
                                        Long playbackMs, Long listenedMs) {
        if (type == MusicBehaviorEventType.PROGRESS) {
            recordPlaybackProgress(userId, playbackSessionId, item, type, playbackMs, listenedMs);
            return new EventWriteResult(false);
        }
        String sessionEventKey = playbackSessionId == null || !isPlaybackLifecycle(type)
                ? null : playbackSessionId + ":" + type.name();
        try {
            jdbc.update("""
                    INSERT INTO music_behavior_event
                        (event_id, playback_session_id, session_event_key, user_id, exposure_id,
                         recommendation_item_id, event_type, playback_ms, listened_ms, reward)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, eventId.toString(), playbackSessionId == null ? null : playbackSessionId.toString(),
                    sessionEventKey, userId, exposureId.toString(), item.id(), type.name(), playbackMs,
                    listenedMs, type.reward());
        } catch (DuplicateKeyException duplicate) {
            return new EventWriteResult(true);
        }

        if (isPlaybackLifecycle(type)) {
            recordPlaybackProgress(userId, playbackSessionId, item, type, playbackMs, listenedMs);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("trackKey", item.trackKey());
        payload.put("track", item.track());
        payload.put("eventType", type.name());
        payload.put("reward", type.reward());
        payload.put("occurredAt", LocalDateTime.now().toString());
        enqueue(userId, "USER_TRACK", userId + ":" + item.trackKey(), "BEHAVIOR_EVENT", payload);
        if (type == MusicBehaviorEventType.LIKE || type == MusicBehaviorEventType.UNLIKE
                || type == MusicBehaviorEventType.DISLIKE
                || type == MusicBehaviorEventType.SAVE || type == MusicBehaviorEventType.UNSAVE) {
            updateExplicitTrackPreference(userId, item, type);
        }
        return new EventWriteResult(false);
    }

    private void recordPlaybackProgress(long userId, UUID playbackSessionId, ExposureItem item,
                                        MusicBehaviorEventType type, Long playbackMs, Long listenedMs) {
        if (playbackSessionId == null) return;
        long observedMs = Math.max(0, playbackMs == null ? 0 : playbackMs);
        int created = jdbc.update("""
                INSERT IGNORE INTO music_playback_session
                    (id, user_id, track_key, max_playback_ms, listened_ms, completed)
                VALUES (?, ?, ?, 0, 0, 0)
                """, playbackSessionId.toString(), userId, item.trackKey());
        Long previousListened = jdbc.queryForObject("""
                SELECT listened_ms FROM music_playback_session
                 WHERE id = ? AND user_id = ?
                """, Long.class, playbackSessionId.toString(), userId);
        long observedListenedMs = Math.max(0, listenedMs == null ? observedMs : listenedMs);
        long delta = Math.max(0, observedListenedMs - (previousListened == null ? 0 : previousListened));
        int completed = type == MusicBehaviorEventType.COMPLETE ? 1 : 0;
        jdbc.update("""
                UPDATE music_playback_session
                   SET max_playback_ms = GREATEST(max_playback_ms, ?),
                       listened_ms = GREATEST(listened_ms, ?),
                       completed = GREATEST(completed, ?), last_event_at = CURRENT_TIMESTAMP(6)
                 WHERE id = ? AND user_id = ?
                """, observedMs, observedListenedMs, completed, playbackSessionId.toString(), userId);
        jdbc.update("""
                INSERT INTO music_user_track_stat
                    (user_id, track_key, play_count, complete_count, skip_count, repeat_count,
                     total_playback_ms, first_played_at, last_played_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE
                    play_count = play_count + VALUES(play_count),
                    complete_count = complete_count + VALUES(complete_count),
                    skip_count = skip_count + VALUES(skip_count),
                    repeat_count = repeat_count + VALUES(repeat_count),
                    total_playback_ms = total_playback_ms + VALUES(total_playback_ms),
                    last_played_at = CURRENT_TIMESTAMP(6)
                """, userId, item.trackKey(), created > 0 ? 1 : 0,
                type == MusicBehaviorEventType.COMPLETE ? 1 : 0,
                type == MusicBehaviorEventType.SKIP ? 1 : 0,
                type == MusicBehaviorEventType.REPEAT ? 1 : 0, delta);
    }

    private static boolean isPlaybackLifecycle(MusicBehaviorEventType type) {
        return type == MusicBehaviorEventType.PLAY_START || type == MusicBehaviorEventType.COMPLETE
                || type == MusicBehaviorEventType.SKIP || type == MusicBehaviorEventType.REPEAT
                || type == MusicBehaviorEventType.PROGRESS;
    }

    @Transactional
    public PreferenceRow addExplicitPreference(long userId, MusicPreferenceType type,
                                                String value, int polarity) {
        String normalized = MusicTextNormalizer.normalize(value);
        jdbc.update("""
                UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ? AND layer = 'L1' AND scope_type = 'GLOBAL'
                   AND preference_type = ? AND normalized_value = ? AND deleted_at IS NULL
                """, userId, type.name(), normalized);
        PreferenceRow row = new PreferenceRow(UUID.randomUUID(), "L1", "GLOBAL", null,
                type.name(), value.strip(), polarity, 1.0, 1, 1, "user", null);
        insertPreference(userId, row);
        enqueue(userId, "PREFERENCE", row.id().toString(), "UPSERT_PREFERENCE", Map.of(
                "userId", userId, "id", row.id().toString(), "type", row.type(),
                "value", row.value(), "polarity", row.polarity(), "confidence", row.confidence(),
                "layer", row.layer()));
        return row;
    }

    @Transactional
    public boolean deletePreference(long userId, UUID id) {
        int changed = jdbc.update("""
                UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                 WHERE id = ? AND user_id = ? AND deleted_at IS NULL
                """, id.toString(), userId);
        if (changed > 0) {
            enqueue(userId, "PREFERENCE", id.toString(), "DELETE_PREFERENCE",
                    Map.of("userId", userId, "id", id.toString()));
        }
        return changed > 0;
    }

    @Transactional
    public int clearLearned(long userId) {
        int changed = jdbc.update("""
                UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ? AND layer IN ('L2', 'L3') AND deleted_at IS NULL
                """, userId);
        enqueue(userId, "USER", Long.toString(userId), "CLEAR_LEARNED",
                Map.of("userId", userId));
        return changed;
    }

    public List<PreferenceRow> effectivePreferences(long userId, UUID conversationId) {
        String conversation = conversationId == null ? "" : conversationId.toString();
        return jdbc.query("""
                SELECT id, layer, scope_type, scope_id, preference_type, preference_value, polarity,
                       confidence, evidence_count, distinct_exposures, source, expires_at
                  FROM music_preference_memory
                 WHERE user_id = ? AND deleted_at IS NULL
                   AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP(6))
                   AND (scope_type = 'GLOBAL' OR (scope_type = 'CONVERSATION' AND scope_id = ?))
                 ORDER BY FIELD(layer, 'L1', 'L3', 'L2'), confidence DESC, updated_at DESC
                """, (rs, row) -> new PreferenceRow(UUID.fromString(rs.getString("id")),
                rs.getString("layer"), rs.getString("scope_type"), rs.getString("scope_id"),
                rs.getString("preference_type"), rs.getString("preference_value"),
                rs.getInt("polarity"), rs.getDouble("confidence"), rs.getInt("evidence_count"),
                rs.getInt("distinct_exposures"), rs.getString("source"),
                rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime()),
                userId, conversation);
    }

    public List<PreferenceRow> profile(long userId) {
        return effectivePreferences(userId, null).stream()
                .filter(row -> !"L3".equals(row.layer()))
                .toList();
    }

    public ProfileStats profileStats(long userId) {
        Integer labeled = jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_behavior_event WHERE user_id = ? AND reward IS NOT NULL
                """, Integer.class, userId);
        Integer exposures = jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_recommendation_exposure WHERE user_id = ?
                """, Integer.class, userId);
        return new ProfileStats(labeled == null ? 0 : labeled, exposures == null ? 0 : exposures);
    }

    public ListeningTotals listeningTotals(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS unique_tracks,
                       COALESCE(SUM(play_count), 0) AS plays,
                       COALESCE(SUM(complete_count), 0) AS completes,
                       COALESCE(SUM(skip_count), 0) AS skips,
                       COALESCE(SUM(repeat_count), 0) AS repeats,
                       COALESCE(SUM(total_playback_ms), 0) AS playback_ms,
                       MIN(first_played_at) AS first_played_at,
                       MAX(last_played_at) AS last_played_at
                  FROM music_user_track_stat WHERE user_id = ?
                """, (rs, row) -> new ListeningTotals(rs.getLong("unique_tracks"), rs.getLong("plays"),
                rs.getLong("completes"), rs.getLong("skips"), rs.getLong("repeats"),
                rs.getLong("playback_ms"), timestamp(rs.getTimestamp("first_played_at")),
                timestamp(rs.getTimestamp("last_played_at"))), userId);
    }

    public List<TrackStatRow> topTracks(long userId, int limit) {
        return jdbc.query("""
                SELECT c.track_key, c.provider, c.provider_track_id, c.title, c.primary_artist, c.album,
                       s.play_count, s.complete_count, s.skip_count, s.repeat_count,
                       s.total_playback_ms, s.last_played_at
                  FROM music_user_track_stat s
                  JOIN music_catalog_track c ON c.track_key = s.track_key
                 WHERE s.user_id = ?
                 ORDER BY s.play_count DESC, s.total_playback_ms DESC, s.last_played_at DESC
                 LIMIT ?
                """, (rs, row) -> new TrackStatRow(rs.getString("track_key"), rs.getString("provider"),
                rs.getString("provider_track_id"), rs.getString("title"), rs.getString("primary_artist"),
                rs.getString("album"), rs.getLong("play_count"), rs.getLong("complete_count"),
                rs.getLong("skip_count"), rs.getLong("repeat_count"), rs.getLong("total_playback_ms"),
                timestamp(rs.getTimestamp("last_played_at"))), userId, Math.max(1, Math.min(limit, 20)));
    }

    public List<ArtistStatRow> topArtists(long userId, int limit) {
        return jdbc.query("""
                SELECT c.primary_artist,
                       COUNT(*) AS unique_tracks,
                       SUM(s.play_count) AS plays,
                       SUM(s.complete_count) AS completes,
                       SUM(s.repeat_count) AS repeats,
                       SUM(s.total_playback_ms) AS playback_ms,
                       MAX(s.last_played_at) AS last_played_at
                  FROM music_user_track_stat s
                  JOIN music_catalog_track c ON c.track_key = s.track_key
                 WHERE s.user_id = ? AND c.primary_artist IS NOT NULL AND c.primary_artist <> ''
                 GROUP BY c.primary_artist
                 ORDER BY plays DESC, playback_ms DESC, last_played_at DESC
                 LIMIT ?
                """, (rs, row) -> new ArtistStatRow(rs.getString("primary_artist"),
                rs.getLong("unique_tracks"), rs.getLong("plays"), rs.getLong("completes"),
                rs.getLong("repeats"), rs.getLong("playback_ms"),
                timestamp(rs.getTimestamp("last_played_at"))), userId, Math.max(1, Math.min(limit, 20)));
    }

    public List<TagStatRow> topTags(long userId, int limit) {
        return jdbc.query("""
                SELECT t.tag_type, t.tag_value, COUNT(DISTINCT s.track_key) AS unique_tracks,
                       SUM(s.play_count) AS plays,
                       SUM(s.total_playback_ms) AS playback_ms,
                       SUM(GREATEST(0, s.play_count + s.complete_count * 2 + s.repeat_count * 3
                                      - s.skip_count) * t.confidence) AS affinity,
                       MAX(t.confidence) AS confidence
                  FROM music_user_track_stat s
                  JOIN (
                        SELECT track_key, tag_type, normalized_value,
                               MAX(tag_value) AS tag_value, MAX(confidence) AS confidence
                          FROM music_track_tag
                         GROUP BY track_key, tag_type, normalized_value
                       ) t ON t.track_key = s.track_key
                 WHERE s.user_id = ?
                 GROUP BY t.tag_type, t.normalized_value, t.tag_value
                 HAVING unique_tracks > 0
                 ORDER BY affinity DESC, plays DESC, playback_ms DESC
                 LIMIT ?
                """, (rs, row) -> new TagStatRow(rs.getString("tag_type"), rs.getString("tag_value"),
                rs.getLong("unique_tracks"), rs.getLong("plays"), rs.getLong("playback_ms"),
                rs.getDouble("affinity"), rs.getDouble("confidence")), userId,
                Math.max(1, Math.min(limit, 30)));
    }

    public boolean shouldEnrichTrack(String trackKey, String source, int refreshDays) {
        Integer fresh = jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_track_enrichment
                 WHERE track_key = ? AND source = ?
                   AND ((status IN ('SUCCESS', 'EMPTY')
                         AND checked_at >= CURRENT_TIMESTAMP(6) - INTERVAL ? DAY)
                        OR (status = 'FAILED'
                            AND checked_at >= CURRENT_TIMESTAMP(6) - INTERVAL 1 DAY))
                """, Integer.class, trackKey, source, Math.max(1, refreshDays));
        return fresh == null || fresh == 0;
    }

    @Transactional
    public void saveTrackEnrichment(String trackKey, String source, String sourceKey,
                                    String status, String errorMessage, List<TrackTagRow> tags) {
        for (TrackTagRow tag : tags == null ? List.<TrackTagRow>of() : tags) {
            upsertTrackTag(trackKey, tag.type(), tag.value(), source, tag.confidence());
        }
        jdbc.update("""
                INSERT INTO music_track_enrichment
                    (track_key, source, source_key, status, checked_at, error_message)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?)
                ON DUPLICATE KEY UPDATE source_key = VALUES(source_key), status = VALUES(status),
                    checked_at = CURRENT_TIMESTAMP(6), error_message = VALUES(error_message)
                """, trackKey, source, sourceKey, status,
                errorMessage == null ? null : truncate(errorMessage, 500));
    }

    private void upsertExposureTags(String trackKey, ExposureTrack item) {
        String source = item.reasonCodes().contains("QQ_ALBUM_PAGE") ? "qq_album"
                : item.reasonCodes().contains("QQ_PUBLIC_PLAYLIST") ? "qq_playlist" : "recommendation";
        double confidence = "qq_album".equals(source) ? 0.92 : "qq_playlist".equals(source) ? 0.58 : 0.50;
        for (String raw : item.tags()) {
            if (raw == null || raw.isBlank()) continue;
            int separator = raw.indexOf(':');
            String requestedType = separator > 0 ? raw.substring(0, separator).strip().toUpperCase() : "TAG";
            boolean typed = List.of("GENRE", "LANGUAGE", "MOOD", "SCENE", "TAG").contains(requestedType);
            String type = typed ? requestedType : "TAG";
            String value = separator > 0 && typed
                    ? raw.substring(separator + 1).strip() : raw.strip();
            upsertTrackTag(trackKey, type, value, source, confidence);
        }
    }

    private void upsertTrackTag(String trackKey, String type, String value, String source, double confidence) {
        if (value == null || value.isBlank()) return;
        String normalized = MusicTextNormalizer.normalize(value);
        if (normalized.isBlank()) return;
        jdbc.update("""
                INSERT INTO music_track_tag
                    (track_key, tag_type, tag_value, normalized_value, source, confidence)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE tag_value = VALUES(tag_value),
                    confidence = GREATEST(confidence, VALUES(confidence)), updated_at = CURRENT_TIMESTAMP(6)
                """, trackKey, truncate(type.toUpperCase(), 24), truncate(value.strip(), 120),
                truncate(normalized, 120), truncate(source, 32), Math.max(0, Math.min(1, confidence)));
    }

    public List<String> recentContextRejections(long userId, UUID conversationId) {
        if (conversationId == null) return List.of();
        return jdbc.queryForList("""
                SELECT DISTINCT track_id FROM music_knowledge_feedback
                 WHERE user_id = ? AND conversation_id = ? AND action = 'NOT_RELEVANT'
                   AND track_id IS NOT NULL AND created_at >= CURRENT_TIMESTAMP(6) - INTERVAL 24 HOUR
                """, String.class, userId, conversationId.toString());
    }

    public List<String> explicitDislikedTrackKeys(long userId) {
        return jdbc.queryForList("""
                SELECT normalized_value FROM music_preference_memory
                 WHERE user_id = ? AND layer = 'L1' AND preference_type = 'TRACK'
                   AND polarity = -1 AND deleted_at IS NULL
                   AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP(6))
                """, String.class, userId);
    }

    public Map<String, Integer> explicitTrackPolarities(long userId) {
        Map<String, Integer> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT normalized_value, polarity FROM music_preference_memory
                 WHERE user_id = ? AND layer = 'L1' AND preference_type = 'TRACK'
                   AND deleted_at IS NULL AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP(6))
                 ORDER BY updated_at
                """, rs -> {
            while (rs.next()) result.put(rs.getString("normalized_value"), rs.getInt("polarity"));
        }, userId);
        return Map.copyOf(result);
    }

    public Map<String, TrackSignal> trackSignals(long userId) {
        Map<String, TrackSignal> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT i.track_key, COUNT(DISTINCT i.exposure_id) AS exposure_count,
                       MAX(x.created_at) AS last_exposed_at,
                       COALESCE(SUM(CASE WHEN e.reward > 0 THEN e.reward ELSE 0 END), 0) AS positive_reward,
                       COALESCE(SUM(CASE WHEN e.reward < 0 THEN -e.reward ELSE 0 END), 0) AS negative_reward
                  FROM music_recommendation_item i
                  JOIN music_recommendation_exposure x ON x.id = i.exposure_id
                  LEFT JOIN music_behavior_event e ON e.recommendation_item_id = i.id AND e.user_id = x.user_id
                 WHERE x.user_id = ? AND x.created_at >= CURRENT_TIMESTAMP(6) - INTERVAL 180 DAY
                 GROUP BY i.track_key
                """, rs -> {
            while (rs.next()) {
                result.put(rs.getString("track_key"), new TrackSignal(
                        rs.getInt("exposure_count"), rs.getTimestamp("last_exposed_at").toLocalDateTime(),
                        rs.getDouble("positive_reward"), rs.getDouble("negative_reward")));
            }
        }, userId);
        return Map.copyOf(result);
    }

    @Transactional
    public void rememberConversationContext(long userId, UUID conversationId,
                                            List<ContextPreference> preferences) {
        jdbc.update("""
                UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ? AND layer = 'L3' AND scope_type = 'CONVERSATION'
                   AND scope_id = ? AND deleted_at IS NULL
                """, userId, conversationId.toString());
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        for (ContextPreference preference : preferences.stream().distinct().toList()) {
            if (preference.value() == null || preference.value().isBlank()) continue;
            insertPreference(userId, new PreferenceRow(UUID.randomUUID(), "L3", "CONVERSATION",
                    conversationId.toString(), preference.type().name(), preference.value().strip(),
                    preference.polarity(), 1.0, 1, 1, "current_request", expiresAt));
        }
    }

    public Optional<PolicyRow> policy(String requestedVersion) {
        List<PolicyRow> rows = jdbc.query("""
                SELECT version, status, coefficients, labeled_events, exposures
                  FROM music_rank_policy WHERE version = ? AND status = 'PASSED'
                """, (rs, row) -> new PolicyRow(rs.getString("version"), rs.getString("status"),
                readDoubleMap(rs.getString("coefficients")), rs.getInt("labeled_events"),
                rs.getInt("exposures")), requestedVersion);
        if (!rows.isEmpty()) return Optional.of(rows.get(0));
        return jdbc.query("""
                SELECT version, status, coefficients, labeled_events, exposures
                  FROM music_rank_policy WHERE version = 'baseline-v1'
                """, (rs, row) -> new PolicyRow(rs.getString("version"), rs.getString("status"),
                readDoubleMap(rs.getString("coefficients")), rs.getInt("labeled_events"),
                rs.getInt("exposures"))).stream().findFirst();
    }

    public List<LearningEvidence> learningEvidence() {
        return jdbc.query("""
                SELECT e.user_id, e.exposure_id, e.event_type, e.reward,
                       i.track_key, i.track_snapshot, i.feature_snapshot, e.created_at
                  FROM music_behavior_event e
                  JOIN music_recommendation_item i ON i.id = e.recommendation_item_id
                 WHERE e.reward IS NOT NULL
                   AND e.created_at >= CURRENT_TIMESTAMP(6) - INTERVAL 30 DAY
                 ORDER BY e.user_id, e.created_at
                """, (rs, row) -> new LearningEvidence(rs.getLong("user_id"),
                UUID.fromString(rs.getString("exposure_id")), rs.getString("event_type"),
                rs.getDouble("reward"), rs.getString("track_key"),
                readTrack(rs.getString("track_snapshot")), readMap(rs.getString("feature_snapshot")),
                rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public int latestCandidateLabels() {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(labeled_events), 0) FROM music_rank_policy WHERE version <> 'baseline-v1'
                """, Integer.class);
        return value == null ? 0 : value;
    }

    public void savePolicyCandidate(String version, String status, Map<String, Double> coefficients,
                                    Map<String, Object> metrics, LocalDateTime trainingStarted,
                                    LocalDateTime trainingEnded, int labeledEvents, int exposures) {
        jdbc.update("""
                INSERT INTO music_rank_policy
                    (version, status, coefficients, metrics, training_started_at, training_ended_at,
                     labeled_events, exposures)
                VALUES (?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?, ?)
                """, version, status, json(coefficients), json(metrics), Timestamp.valueOf(trainingStarted),
                Timestamp.valueOf(trainingEnded), labeledEvents, exposures);
    }

    @Transactional
    public void replaceInferredPreferences(long userId, List<PreferenceRow> preferences) {
        jdbc.update("""
                UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ? AND layer = 'L2' AND deleted_at IS NULL
                """, userId);
        for (PreferenceRow preference : preferences) {
            insertPreference(userId, preference);
            enqueue(userId, "PREFERENCE", preference.id().toString(), "UPSERT_PREFERENCE", Map.of(
                    "userId", userId, "id", preference.id().toString(), "type", preference.type(),
                    "value", preference.value(), "polarity", preference.polarity(),
                    "confidence", preference.confidence(), "layer", preference.layer()));
        }
    }

    public List<OutboxRow> pendingOutbox(int limit) {
        return jdbc.query("""
                SELECT id, user_id, event_type, payload, attempts
                  FROM music_graph_outbox
                 WHERE status IN ('PENDING', 'RETRY') AND next_attempt_at <= CURRENT_TIMESTAMP(6)
                 ORDER BY id LIMIT ?
                """, (rs, row) -> new OutboxRow(rs.getLong("id"),
                rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                rs.getString("event_type"), readMap(rs.getString("payload")), rs.getInt("attempts")), limit);
    }

    public void markOutboxProcessed(long id) {
        jdbc.update("""
                UPDATE music_graph_outbox SET status = 'DONE', processed_at = CURRENT_TIMESTAMP(6),
                    last_error = NULL WHERE id = ?
                """, id);
    }

    public void markOutboxRetry(long id, int attempts, String error) {
        long delaySeconds = Math.min(3600, Math.max(5, 1L << Math.min(10, attempts)));
        jdbc.update("""
                UPDATE music_graph_outbox SET status = 'RETRY', attempts = ?, last_error = ?,
                    next_attempt_at = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL ? SECOND)
                 WHERE id = ?
                """, attempts, truncate(error, 500), delaySeconds, id);
    }

    public void markGraphProjected(String trackKey, String contentHash,
                                   String model, Integer dimensions, boolean embedded) {
        jdbc.update("""
                UPDATE music_catalog_track
                   SET graph_projected_hash = ?,
                       embedding_model = CASE WHEN ? THEN ? ELSE embedding_model END,
                       embedding_dimensions = CASE WHEN ? THEN ? ELSE embedding_dimensions END,
                       embedding_content_hash = CASE WHEN ? THEN ? ELSE embedding_content_hash END
                 WHERE track_key = ?
                """, contentHash, embedded, model, embedded, dimensions,
                embedded, contentHash, trackKey);
    }

    @Transactional
    public RetentionResult purgeExpiredOperationalData() {
        int events = jdbc.update("""
                DELETE FROM music_behavior_event
                 WHERE created_at < CURRENT_TIMESTAMP(6) - INTERVAL 180 DAY
                """);
        int exposures = jdbc.update("""
                DELETE x FROM music_recommendation_exposure x
                 WHERE x.created_at < CURRENT_TIMESTAMP(6) - INTERVAL 180 DAY
                   AND NOT EXISTS (SELECT 1 FROM music_behavior_event e WHERE e.exposure_id = x.id)
                """);
        return new RetentionResult(events, exposures);
    }

    private void updateExplicitTrackPreference(long userId, ExposureItem item, MusicBehaviorEventType type) {
        int polarity = type == MusicBehaviorEventType.DISLIKE ? -1 : 1;
        if (type == MusicBehaviorEventType.UNLIKE) {
            jdbc.update("""
                    UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                     WHERE user_id = ? AND layer = 'L1' AND preference_type = 'TRACK'
                       AND normalized_value = ? AND source = 'LIKE' AND deleted_at IS NULL
                    """, userId, item.trackKey());
            return;
        }
        if (type == MusicBehaviorEventType.UNSAVE) {
            jdbc.update("""
                    UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                     WHERE user_id = ? AND layer = 'L1' AND preference_type = 'TRACK'
                       AND normalized_value = ? AND source = 'SAVE' AND deleted_at IS NULL
                    """, userId, item.trackKey());
            return;
        }
        if (type == MusicBehaviorEventType.SAVE) {
            Integer existingPositive = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM music_preference_memory
                     WHERE user_id = ? AND layer = 'L1' AND preference_type = 'TRACK'
                       AND normalized_value = ? AND polarity = 1 AND deleted_at IS NULL
                    """, Integer.class, userId, item.trackKey());
            if (existingPositive != null && existingPositive > 0) return;
        }
        jdbc.update("""
                UPDATE music_preference_memory SET deleted_at = CURRENT_TIMESTAMP(6)
                 WHERE user_id = ? AND layer = 'L1' AND preference_type = 'TRACK'
                   AND normalized_value = ? AND deleted_at IS NULL
                """, userId, item.trackKey());
        String display = item.track().name() + (primaryArtist(item.track()) == null
                ? "" : " — " + primaryArtist(item.track()));
        PreferenceRow row = new PreferenceRow(UUID.randomUUID(), "L1", "GLOBAL", null,
                MusicPreferenceType.TRACK.name(), display, polarity, 1.0, 1, 1, type.name(), null);
        insertPreference(userId, row, item.trackKey());
    }

    public boolean isTrackLiked(long userId, UUID exposureId, String trackId) {
        ExposureItem item = findOwnedExposureItem(userId, exposureId, trackId)
                .orElseThrow(() -> new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "歌曲不属于当前用户的推荐曝光"));
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM music_preference_memory
                 WHERE user_id = ? AND layer = 'L1' AND preference_type = 'TRACK'
                   AND normalized_value = ? AND source = 'LIKE' AND polarity = 1
                   AND deleted_at IS NULL
                """, Integer.class, userId, item.trackKey());
        return count != null && count > 0;
    }

    public boolean isTrackSaved(long userId, UUID exposureId, String trackId) {
        ExposureItem item = findOwnedExposureItem(userId, exposureId, trackId)
                .orElseThrow(() -> new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "歌曲不属于当前用户的推荐曝光"));
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM music_playlist_track t
                  JOIN music_playlist p ON p.id = t.playlist_id
                 WHERE p.user_id = ? AND p.deleted_at IS NULL AND t.deleted_at IS NULL
                   AND p.playlist_type IN ('CUSTOM', 'RECOMMENDED')
                   AND t.track_key = ?
                """, Integer.class, userId, item.trackKey());
        return count != null && count > 0;
    }

    private void insertPreference(long userId, PreferenceRow row) {
        insertPreference(userId, row, MusicTextNormalizer.normalize(row.value()));
    }

    private void insertPreference(long userId, PreferenceRow row, String normalized) {
        jdbc.update("""
                INSERT INTO music_preference_memory
                    (id, user_id, layer, scope_type, scope_id, preference_type, preference_value,
                     normalized_value, polarity, confidence, evidence_count, distinct_exposures,
                     source, valid_from, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?)
                """, row.id().toString(), userId, row.layer(), row.scopeType(), row.scopeId(), row.type(),
                row.value(), normalized, row.polarity(), row.confidence(), row.evidenceCount(),
                row.distinctExposures(), row.source(),
                row.expiresAt() == null ? null : Timestamp.valueOf(row.expiresAt()));
    }

    private void enqueue(Long userId, String aggregateType, String aggregateId,
                         String eventType, Object payload) {
        jdbc.update("""
                INSERT INTO music_graph_outbox
                    (user_id, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, ?, ?, ?, CAST(? AS JSON))
                """, userId, aggregateType, aggregateId, eventType, json(payload));
    }

    private void enqueueLatestSong(Long userId, String trackKey, Object payload) {
        String serialized = json(payload);
        int changed = jdbc.update("""
                UPDATE music_graph_outbox
                   SET user_id = ?, payload = CAST(? AS JSON), status = 'PENDING', attempts = 0,
                       next_attempt_at = CURRENT_TIMESTAMP(6), last_error = NULL
                 WHERE aggregate_type = 'SONG' AND aggregate_id = ? AND event_type = 'UPSERT_SONG'
                   AND status IN ('PENDING', 'RETRY')
                """, userId, serialized, trackKey);
        if (changed == 0) enqueue(userId, "SONG", trackKey, "UPSERT_SONG", payload);
    }

    private Optional<TrackProjectionState> projectionState(String trackKey) {
        return jdbc.query("""
                SELECT graph_projected_hash, embedding_content_hash
                  FROM music_catalog_track WHERE track_key = ?
                """, (rs, row) -> new TrackProjectionState(
                rs.getString("graph_projected_hash"), rs.getString("embedding_content_hash")), trackKey)
                .stream().findFirst();
    }

    private MusicTrackBo readTrack(String json) {
        try {
            return objectMapper.readValue(json, MusicTrackBo.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored music track snapshot", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored music JSON", exception);
        }
    }

    private Map<String, Double> readDoubleMap(String json) {
        Map<String, Object> raw = readMap(json);
        Map<String, Double> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (value instanceof Number number) result.put(key, number.doubleValue());
        });
        return Map.copyOf(result);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Music data cannot be serialized", exception);
        }
    }

    private static String primaryArtist(MusicTrackBo track) {
        return track.artists() == null || track.artists().isEmpty() ? null : track.artists().get(0);
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private static String truncate(String value, int max) {
        String actual = value == null ? "unknown" : value;
        return actual.length() <= max ? actual : actual.substring(0, max);
    }

    public record ExposureTrack(MusicTrackBo track, Map<String, Integer> sourceRanks,
                                Map<String, Object> features, List<String> reasonCodes,
                                List<String> tags, double finalScore, boolean exploration) {
        public ExposureTrack {
            sourceRanks = sourceRanks == null ? Map.of() : Map.copyOf(sourceRanks);
            features = features == null ? Map.of() : Map.copyOf(features);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record ExposureItem(long id, String trackKey, String provider, String trackId,
                               MusicTrackBo track, Map<String, Object> features, int position,
                               UUID conversationId, String policyVersion) {
    }

    public record RecentExposureTrack(String exposureId, String trackKey,
                                      String title, String primaryArtist) {
    }

    public record EventWriteResult(boolean duplicate) {
    }

    public record PreferenceRow(UUID id, String layer, String scopeType, String scopeId,
                                String type, String value, int polarity, double confidence,
                                int evidenceCount, int distinctExposures, String source,
                                LocalDateTime expiresAt) {
    }

    public record ProfileStats(int labeledEvents, int exposures) {
    }

    public record ListeningTotals(long uniqueTracks, long plays, long completes, long skips, long repeats,
                                  long playbackMs, LocalDateTime firstPlayedAt, LocalDateTime lastPlayedAt) {
    }

    public record TrackStatRow(String trackKey, String provider, String trackId, String title,
                               String artist, String album, long plays, long completes, long skips,
                               long repeats, long playbackMs, LocalDateTime lastPlayedAt) {
    }

    public record ArtistStatRow(String artist, long uniqueTracks, long plays, long completes,
                                long repeats, long playbackMs, LocalDateTime lastPlayedAt) {
    }

    public record TagStatRow(String type, String value, long uniqueTracks, long plays,
                             long playbackMs, double affinity, double confidence) {
    }

    public record TrackTagRow(String type, String value, double confidence) {
    }

    public record TrackSignal(int exposureCount, LocalDateTime lastExposedAt,
                              double positiveReward, double negativeReward) {
    }

    public record ContextPreference(MusicPreferenceType type, String value, int polarity) {
    }

    public record PolicyRow(String version, String status, Map<String, Double> coefficients,
                            int labeledEvents, int exposures) {
    }

    public record LearningEvidence(long userId, UUID exposureId, String eventType, double reward,
                                   String trackKey, MusicTrackBo track, Map<String, Object> features,
                                   LocalDateTime createdAt) {
    }

    public record OutboxRow(long id, Long userId, String eventType,
                            Map<String, Object> payload, int attempts) {
    }

    private record TrackProjectionState(String graphProjectedHash, String embeddingContentHash) {
    }

    public record RetentionResult(int events, int exposures) {
    }
}
