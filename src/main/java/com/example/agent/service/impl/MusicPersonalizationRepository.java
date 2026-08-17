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
        requireOwnedConversation(userId, conversationId);
        jdbc.update("""
                INSERT INTO music_recommendation_exposure
                    (id, user_id, conversation_id, description, plan_json, policy_version,
                     personalization_status)
                VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, ?)
                """, exposureId.toString(), userId, conversationId.toString(), description,
                json(plan), policyVersion, status.name());

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
            jdbc.update("""
                    INSERT INTO music_recommendation_item
                        (exposure_id, track_key, provider, provider_track_id, display_position,
                         track_snapshot, source_ranks, feature_snapshot, final_score, reason_codes, exploration)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), CAST(? AS JSON), ?,
                            CAST(? AS JSON), ?)
                    """, exposureId.toString(), trackKey, track.provider(), track.id(), position,
                    json(track), json(item.sourceRanks()), json(item.features()), item.finalScore(),
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
    public EventWriteResult recordEvent(long userId, UUID eventId, UUID exposureId,
                                        ExposureItem item, MusicBehaviorEventType type, Long playbackMs) {
        try {
            jdbc.update("""
                    INSERT INTO music_behavior_event
                        (event_id, user_id, exposure_id, recommendation_item_id, event_type, playback_ms, reward)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, eventId.toString(), userId, exposureId.toString(), item.id(), type.name(),
                    playbackMs, type.reward());
        } catch (DuplicateKeyException duplicate) {
            return new EventWriteResult(true);
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

    public record EventWriteResult(boolean duplicate) {
    }

    public record PreferenceRow(UUID id, String layer, String scopeType, String scopeId,
                                String type, String value, int polarity, double confidence,
                                int evidenceCount, int distinctExposures, String source,
                                LocalDateTime expiresAt) {
    }

    public record ProfileStats(int labeledEvents, int exposures) {
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
