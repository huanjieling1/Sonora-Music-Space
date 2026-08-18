ALTER TABLE music_recommendation_exposure
    ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''
        COMMENT '规范化推荐目标 SHA-256，用于批次新颖度隔离' AFTER personalization_status,
    ADD COLUMN batch_sequence INT UNSIGNED NOT NULL DEFAULT 1
        COMMENT '同一推荐目标在当前会话中的批次序号' AFTER request_fingerprint,
    ADD COLUMN refresh_source VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'STANDARD'
        COMMENT 'STANDARD 或用户显式 REFRESH' AFTER batch_sequence,
    ADD KEY idx_music_exposure_novelty
        (user_id, conversation_id, request_fingerprint, batch_sequence);

UPDATE music_recommendation_exposure
   SET request_fingerprint = SHA2(LOWER(TRIM(description)), 256)
 WHERE request_fingerprint = '';
