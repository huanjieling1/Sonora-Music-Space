package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicTrackRelationBo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class MusicKnowledgeRepository {
    private final JdbcTemplate jdbc;

    public MusicKnowledgeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EntityRow> findCorrection(long userId, String normalizedDescription) {
        List<EntityRow> rows = jdbc.query("""
                SELECT e.id, e.canonical_name, e.entity_type, e.related_terms, e.confidence, 'user' AS source
                  FROM music_knowledge_feedback f
                  JOIN music_knowledge_entity e
                    ON e.normalized_name = REPLACE(LOWER(f.corrected_entity_name), ' ', '')
                   AND e.entity_type = f.corrected_entity_type
                 WHERE f.user_id = ? AND f.action = 'CORRECT_ENTITY' AND f.normalized_description = ?
                 ORDER BY f.created_at DESC LIMIT 1
                """, this::mapEntity, userId, normalizedDescription);
        return rows.stream().findFirst();
    }

    /** Unscoped callers must never see another user's correction. */
    public Optional<EntityRow> findCorrection(String normalizedDescription) {
        return Optional.empty();
    }

    public List<AliasRow> aliases() {
        return jdbc.query("""
                SELECT a.entity_id, a.alias_name, a.normalized_alias, a.priority,
                       e.canonical_name, e.entity_type, e.related_terms, e.confidence, e.source
                  FROM music_knowledge_alias a
                  JOIN music_knowledge_entity e ON e.id = a.entity_id
                 WHERE a.source <> 'user'
                 ORDER BY CHAR_LENGTH(a.normalized_alias) DESC, a.priority ASC
                """, (rs, row) -> new AliasRow(
                rs.getLong("entity_id"), rs.getString("alias_name"), rs.getString("normalized_alias"),
                rs.getInt("priority"), rs.getString("canonical_name"),
                MusicEntityType.valueOf(rs.getString("entity_type")), terms(rs.getString("related_terms")),
                rs.getDouble("confidence"), rs.getString("source")));
    }

    public List<String> aliasesFor(long entityId) {
        return jdbc.queryForList("""
                SELECT alias_name FROM music_knowledge_alias WHERE entity_id = ? ORDER BY priority, id
                """, String.class, entityId);
    }

    public List<MusicTrackRelationBo> relationsFor(long entityId) {
        return jdbc.query("""
                SELECT track_title, artist_name, album_name, relation_type, relation_label,
                       source, source_url, confidence
                  FROM music_knowledge_track_relation WHERE entity_id = ? ORDER BY confidence DESC, id
                """, (rs, row) -> new MusicTrackRelationBo(
                rs.getString("track_title"), rs.getString("artist_name"), rs.getString("album_name"),
                rs.getString("relation_type"), rs.getString("relation_label"), rs.getString("source"),
                rs.getString("source_url"), rs.getDouble("confidence")), entityId);
    }

    public List<String> rejectedTracks(long userId, String canonicalName) {
        if (!StringUtils.hasText(canonicalName)) {
            return List.of();
        }
        return jdbc.queryForList("""
                SELECT DISTINCT track_id FROM music_knowledge_feedback
                 WHERE user_id = ? AND action = 'NOT_RELEVANT' AND resolved_entity_name = ?
                   AND track_id IS NOT NULL AND created_at >= CURRENT_TIMESTAMP(6) - INTERVAL 24 HOUR
                """, String.class, userId, canonicalName);
    }

    /** Unscoped knowledge resolution does not apply private feedback. */
    public List<String> rejectedTracks(String canonicalName) {
        return List.of();
    }

    public Optional<CacheRow> cache(String key, String provider) {
        List<CacheRow> rows = jdbc.query("""
                SELECT successful, payload, expires_at FROM music_knowledge_cache
                 WHERE cache_key = ? AND provider = ? AND expires_at > CURRENT_TIMESTAMP(6)
                """, (rs, row) -> new CacheRow(rs.getBoolean("successful"), rs.getString("payload"),
                rs.getTimestamp("expires_at").toLocalDateTime()), key, provider);
        return rows.stream().findFirst();
    }

    public void saveCache(String key, String provider, boolean successful, String payload, LocalDateTime expiresAt) {
        jdbc.update("""
                INSERT INTO music_knowledge_cache(cache_key, provider, successful, payload, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE successful = VALUES(successful), payload = VALUES(payload),
                                        expires_at = VALUES(expires_at), updated_at = CURRENT_TIMESTAMP(6)
                """, key, provider, successful, payload, Timestamp.valueOf(expiresAt));
    }

    @Transactional
    public void saveFeedback(long userId, String conversationId, String searchId,
                             String action, String description,
                             String trackId, String resolvedEntityName,
                             String correctedEntityName, MusicEntityType correctedEntityType) {
        String normalizedDescription = MusicTextNormalizer.normalize(description);
        jdbc.update("""
                INSERT INTO music_knowledge_feedback
                    (user_id, conversation_id, search_id, action, description, normalized_description, track_id,
                     resolved_entity_name, corrected_entity_name, corrected_entity_type)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, conversationId, searchId, action, description, normalizedDescription, trackId,
                resolvedEntityName, correctedEntityName,
                correctedEntityType == null ? null : correctedEntityType.name());
        if ("CORRECT_ENTITY".equals(action) && StringUtils.hasText(correctedEntityName)
                && correctedEntityType != null) {
            ensureEntity(correctedEntityName, correctedEntityType);
            jdbc.update("DELETE FROM music_knowledge_cache WHERE cache_key = ?", normalizedDescription);
        }
    }

    private long ensureEntity(String canonicalName, MusicEntityType type) {
        String normalized = MusicTextNormalizer.normalize(canonicalName);
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM music_knowledge_entity WHERE normalized_name = ? AND entity_type = ? LIMIT 1
                """, Long.class, normalized, type.name());
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbc.update("""
                INSERT INTO music_knowledge_entity
                    (canonical_name, normalized_name, entity_type, source, confidence)
                VALUES (?, ?, ?, 'user', 1.0000)
                """, canonicalName.strip(), normalized, type.name());
        return jdbc.queryForObject("""
                SELECT id FROM music_knowledge_entity WHERE normalized_name = ? AND entity_type = ?
                """, Long.class, normalized, type.name());
    }

    private EntityRow mapEntity(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new EntityRow(rs.getLong("id"), rs.getString("canonical_name"),
                MusicEntityType.valueOf(rs.getString("entity_type")), terms(rs.getString("related_terms")),
                rs.getDouble("confidence"), rs.getString("source"));
    }

    private static List<String> terms(String value) {
        return !StringUtils.hasText(value) ? List.of()
                : Arrays.stream(value.split(",")).map(String::strip).filter(StringUtils::hasText).toList();
    }

    public record EntityRow(long id, String canonicalName, MusicEntityType entityType,
                            List<String> relatedTerms, double confidence, String source) {
    }

    public record AliasRow(long entityId, String aliasName, String normalizedAlias, int priority,
                           String canonicalName, MusicEntityType entityType, List<String> relatedTerms,
                           double confidence, String source) {
    }

    public record CacheRow(boolean successful, String payload, LocalDateTime expiresAt) {
    }
}
