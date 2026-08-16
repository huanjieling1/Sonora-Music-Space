ALTER TABLE music_knowledge_feedback
    ADD COLUMN conversation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER user_id,
    ADD KEY idx_music_feedback_user_conversation (user_id, conversation_id, created_at);

CREATE TABLE music_catalog_track (
    track_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'provider + provider track id 的 SHA-256',
    provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_track_id VARCHAR(255) NOT NULL,
    title VARCHAR(200) NOT NULL,
    primary_artist VARCHAR(200) NULL,
    album VARCHAR(200) NULL,
    content_text VARCHAR(1500) NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    metadata_json JSON NOT NULL,
    embedding_model VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    embedding_dimensions INT NULL,
    first_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (track_key),
    UNIQUE KEY uk_music_catalog_provider_track (provider, provider_track_id),
    KEY idx_music_catalog_content_hash (content_hash),
    KEY idx_music_catalog_last_seen (last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='在线曲库歌曲的可重建元数据缓存';

CREATE TABLE music_recommendation_exposure (
    id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    conversation_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description VARCHAR(500) NOT NULL,
    plan_json JSON NOT NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    personalization_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_music_exposure_user_created (user_id, created_at),
    KEY idx_music_exposure_conversation_created (conversation_id, created_at),
    CONSTRAINT fk_music_exposure_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_music_exposure_conversation FOREIGN KEY (conversation_id) REFERENCES agent_conversation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务端权威推荐曝光';

CREATE TABLE music_recommendation_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    exposure_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    track_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_track_id VARCHAR(255) NOT NULL,
    display_position INT NOT NULL,
    track_snapshot JSON NOT NULL,
    source_ranks JSON NOT NULL,
    feature_snapshot JSON NOT NULL,
    final_score DECIMAL(12,8) NOT NULL,
    reason_codes JSON NOT NULL,
    exploration TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_item_exposure_position (exposure_id, display_position),
    UNIQUE KEY uk_music_item_exposure_track (exposure_id, provider, provider_track_id),
    KEY idx_music_item_track (track_key),
    CONSTRAINT fk_music_item_exposure FOREIGN KEY (exposure_id)
        REFERENCES music_recommendation_exposure (id) ON DELETE CASCADE,
    CONSTRAINT fk_music_item_catalog FOREIGN KEY (track_key) REFERENCES music_catalog_track (track_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='曝光中的歌曲、位置和排序特征快照';

CREATE TABLE music_behavior_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    exposure_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    recommendation_item_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    playback_ms BIGINT UNSIGNED NULL,
    reward DECIMAL(6,3) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_event_user_event (user_id, event_id),
    KEY idx_music_event_user_created (user_id, created_at),
    KEY idx_music_event_exposure (exposure_id, created_at),
    KEY idx_music_event_item (recommendation_item_id, created_at),
    CONSTRAINT fk_music_event_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_music_event_exposure FOREIGN KEY (exposure_id) REFERENCES music_recommendation_exposure (id),
    CONSTRAINT fk_music_event_item FOREIGN KEY (recommendation_item_id) REFERENCES music_recommendation_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户对真实曝光歌曲的幂等行为事件 L0';

CREATE TABLE music_preference_memory (
    id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    layer VARCHAR(4) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'L1/L2/L3',
    scope_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'GLOBAL/CONVERSATION',
    scope_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    preference_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    preference_value VARCHAR(200) NOT NULL,
    normalized_value VARCHAR(200) NOT NULL,
    polarity SMALLINT NOT NULL COMMENT '1=偏好,-1=避开',
    confidence DECIMAL(5,4) NOT NULL,
    evidence_count INT NOT NULL DEFAULT 1,
    distinct_exposures INT NOT NULL DEFAULT 1,
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    valid_from DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    superseded_by VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_music_memory_effective (user_id, layer, deleted_at, expires_at),
    KEY idx_music_memory_scope (user_id, scope_type, scope_id, deleted_at),
    KEY idx_music_memory_value (user_id, preference_type, normalized_value, deleted_at),
    CONSTRAINT fk_music_memory_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可编辑、可过期、可审计的音乐偏好记忆';

CREATE TABLE music_graph_outbox (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_music_outbox_delivery (status, next_attempt_at, id),
    KEY idx_music_outbox_aggregate (aggregate_type, aggregate_id),
    CONSTRAINT fk_music_outbox_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='MySQL 到 Neo4j 的可靠投影队列';

CREATE TABLE music_rank_policy (
    version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    coefficients JSON NOT NULL,
    metrics JSON NULL,
    training_started_at DATETIME(6) NULL,
    training_ended_at DATETIME(6) NULL,
    labeled_events INT NOT NULL DEFAULT 0,
    exposures INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (version),
    KEY idx_music_policy_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可验证和可回滚的排序策略版本';

INSERT INTO music_rank_policy(version, status, coefficients, metrics)
VALUES ('baseline-v1', 'PASSED', JSON_OBJECT(
    'semantic', 0.45,
    'structured', 0.30,
    'rrf', 0.25,
    'personal', 0.06,
    'freshness', 0.035,
    'longtail', 0.025,
    'exposurePenalty', -0.06
), JSON_OBJECT('source', 'curated-baseline'));
