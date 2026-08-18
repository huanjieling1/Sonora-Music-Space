ALTER TABLE music_behavior_event
    ADD COLUMN playback_session_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER event_id,
    ADD COLUMN session_event_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER playback_session_id,
    ADD COLUMN listened_ms BIGINT UNSIGNED NULL AFTER playback_ms,
    ADD UNIQUE KEY uk_music_event_session_type (user_id, session_event_key),
    ADD KEY idx_music_event_session (user_id, playback_session_id, created_at);

CREATE TABLE music_track_tag (
    track_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tag_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'GENRE/LANGUAGE/MOOD/SCENE/TAG',
    tag_value VARCHAR(120) NOT NULL,
    normalized_value VARCHAR(120) NOT NULL,
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    first_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (track_key, tag_type, normalized_value, source),
    KEY idx_music_track_tag_value (tag_type, normalized_value),
    CONSTRAINT fk_music_track_tag_catalog FOREIGN KEY (track_key)
        REFERENCES music_catalog_track (track_key) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='歌曲标签及其来源和可信度';

CREATE TABLE music_track_enrichment (
    track_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_key VARCHAR(255) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    error_message VARCHAR(500) NULL,
    PRIMARY KEY (track_key, source),
    KEY idx_music_enrichment_checked (source, checked_at),
    CONSTRAINT fk_music_enrichment_catalog FOREIGN KEY (track_key)
        REFERENCES music_catalog_track (track_key) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外部歌曲标签补全状态，避免重复抓取';

CREATE TABLE music_playback_session (
    id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    track_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    max_playback_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    listened_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_event_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id, user_id),
    KEY idx_music_session_user_started (user_id, started_at),
    KEY idx_music_session_track (track_key),
    CONSTRAINT fk_music_session_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_music_session_track FOREIGN KEY (track_key) REFERENCES music_catalog_track (track_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨刷新续播仍保持唯一的播放会话';

CREATE TABLE music_user_track_stat (
    user_id BIGINT UNSIGNED NOT NULL,
    track_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    play_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    complete_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    skip_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    repeat_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_playback_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    first_played_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_played_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, track_key),
    KEY idx_music_user_track_recent (user_id, last_played_at),
    KEY idx_music_user_track_plays (user_id, play_count),
    CONSTRAINT fk_music_user_track_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_music_user_track_catalog FOREIGN KEY (track_key)
        REFERENCES music_catalog_track (track_key) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户歌曲长期累计统计，不受原始事件保留期影响';
