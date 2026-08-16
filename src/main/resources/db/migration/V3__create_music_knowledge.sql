CREATE TABLE music_knowledge_entity (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    canonical_name VARCHAR(160) NOT NULL,
    normalized_name VARCHAR(160) NOT NULL,
    entity_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_entity_id BIGINT UNSIGNED NULL,
    related_terms VARCHAR(500) NULL,
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_ref VARCHAR(255) NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_entity_normalized_type (normalized_name, entity_type),
    KEY idx_music_entity_parent (parent_entity_id),
    CONSTRAINT fk_music_entity_parent FOREIGN KEY (parent_entity_id) REFERENCES music_knowledge_entity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐 Agent 结构化实体知识';

CREATE TABLE music_knowledge_alias (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    entity_id BIGINT UNSIGNED NOT NULL,
    alias_name VARCHAR(160) NOT NULL,
    normalized_alias VARCHAR(160) NOT NULL,
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_alias_normalized (normalized_alias),
    KEY idx_music_alias_entity (entity_id),
    CONSTRAINT fk_music_alias_entity FOREIGN KEY (entity_id) REFERENCES music_knowledge_entity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='音乐实体中英文别名';

CREATE TABLE music_knowledge_track_relation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    entity_id BIGINT UNSIGNED NOT NULL,
    track_title VARCHAR(200) NOT NULL,
    normalized_track_title VARCHAR(200) NOT NULL,
    artist_name VARCHAR(200) NULL,
    normalized_artist_name VARCHAR(200) NULL,
    album_name VARCHAR(200) NULL,
    relation_type VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    relation_label VARCHAR(80) NOT NULL,
    source VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_url VARCHAR(500) NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_music_relation_entity_track_artist (entity_id, normalized_track_title, normalized_artist_name),
    KEY idx_music_relation_entity (entity_id),
    CONSTRAINT fk_music_relation_entity FOREIGN KEY (entity_id) REFERENCES music_knowledge_entity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='作品、赛事与歌曲的确认关系';

CREATE TABLE music_knowledge_cache (
    cache_key VARCHAR(190) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    successful TINYINT(1) NOT NULL,
    payload MEDIUMTEXT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (cache_key, provider),
    KEY idx_music_cache_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公开音乐知识查询缓存';

CREATE TABLE music_knowledge_feedback (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    search_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    action VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    description VARCHAR(500) NOT NULL,
    normalized_description VARCHAR(500) NOT NULL,
    track_id VARCHAR(255) NULL,
    resolved_entity_name VARCHAR(160) NULL,
    corrected_entity_name VARCHAR(160) NULL,
    corrected_entity_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_music_feedback_description (normalized_description(190), created_at),
    KEY idx_music_feedback_entity_track (resolved_entity_name, track_id),
    KEY idx_music_feedback_user (user_id, created_at),
    CONSTRAINT fk_music_feedback_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户对音乐 Agent 理解和结果的纠错';

INSERT INTO music_knowledge_entity
    (id, canonical_name, normalized_name, entity_type, parent_entity_id, related_terms, source, source_ref, confidence)
VALUES
    (1, 'VALORANT', 'valorant', 'GAME', NULL, 'cinematic,electronic,competitive gaming,energetic', 'curated', 'riot-valorant', 1.0000),
    (2, 'VALORANT Champions', 'valorantchampions', 'EVENT', 1, 'cinematic,electronic,anthem,competitive gaming', 'curated', 'valorant-champions', 1.0000);

INSERT INTO music_knowledge_alias (entity_id, alias_name, normalized_alias, source, priority)
VALUES
    (1, 'VALORANT', 'valorant', 'curated', 10),
    (1, '无畏契约', '无畏契约', 'curated', 10),
    (1, '瓦罗兰特', '瓦罗兰特', 'curated', 20),
    (1, '瓦', '瓦', 'curated', 80),
    (1, 'VCT', 'vct', 'curated', 40),
    (2, 'VALORANT Champions', 'valorantchampions', 'curated', 10),
    (2, '无畏契约冠军赛', '无畏契约冠军赛', 'curated', 10),
    (2, 'VCT Champions', 'vctchampions', 'curated', 10);

INSERT INTO music_knowledge_track_relation
    (entity_id, track_title, normalized_track_title, artist_name, normalized_artist_name, album_name,
     relation_type, relation_label, source, source_url, confidence)
VALUES
    (2, 'Die For You', 'dieforyou', 'Grabbitz', 'grabbitz', NULL,
     'OFFICIAL_EVENT_ANTHEM', '官方赛事歌曲', 'curated', NULL, 1.0000),
    (2, 'Fire Again', 'fireagain', 'Ashnikko', 'ashnikko', NULL,
     'OFFICIAL_EVENT_ANTHEM', '官方赛事歌曲', 'curated', NULL, 1.0000),
    (2, 'TICKING AWAY', 'tickingaway', 'Grabbitz', 'grabbitz', NULL,
     'OFFICIAL_EVENT_ANTHEM', '官方赛事歌曲', 'curated', NULL, 1.0000),
    (2, 'SUPERPOWER', 'superpower', NULL, NULL, NULL,
     'OFFICIAL_EVENT_ANTHEM', '官方赛事歌曲', 'curated', NULL, 0.9800),
    (2, 'Last Shot', 'lastshot', NULL, NULL, NULL,
     'OFFICIAL_EVENT_ANTHEM', '官方赛事歌曲', 'curated', NULL, 0.9800);
