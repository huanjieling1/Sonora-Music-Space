ALTER TABLE music_playlist
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '歌单逻辑删除时间，空值表示有效' AFTER updated_at,
    ADD KEY idx_music_playlist_user_deleted_updated (user_id, deleted_at, updated_at);

ALTER TABLE music_playlist_track
    DROP INDEX uk_music_playlist_position,
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '歌单歌曲逻辑删除时间，空值表示有效' AFTER added_at,
    ADD KEY idx_music_playlist_track_active_position (playlist_id, deleted_at, position);
