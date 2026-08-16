CREATE TABLE music_playlist (
    id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    playlist_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    system_key VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    cover_url VARCHAR(1500) NULL,
    source_description VARCHAR(500) NULL,
    source_exposure_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    policy_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    editable TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_playlist_user_system (user_id, system_key),
    KEY idx_music_playlist_user_updated (user_id, updated_at),
    CONSTRAINT fk_music_playlist_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户自建、推荐和系统音乐歌单';

CREATE TABLE music_playlist_track (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    playlist_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    track_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    position INT NOT NULL,
    track_snapshot JSON NOT NULL,
    added_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_playlist_track (playlist_id, track_key),
    UNIQUE KEY uk_music_playlist_position (playlist_id, position),
    KEY idx_music_playlist_track_key (track_key),
    CONSTRAINT fk_music_playlist_track_playlist FOREIGN KEY (playlist_id)
        REFERENCES music_playlist (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='歌单歌曲及可长期播放的元数据快照';
