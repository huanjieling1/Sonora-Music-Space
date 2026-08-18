DELIMITER $$

CREATE PROCEDURE sonora_set_profile_column_comment(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_comment VARCHAR(1024)
)
BEGIN
    DECLARE v_definition LONGTEXT;
    SELECT CONCAT(
               '`', REPLACE(COLUMN_NAME, '`', '``'), '` ', COLUMN_TYPE,
               IF(CHARACTER_SET_NAME IS NULL, '',
                  CONCAT(' CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME)),
               IF(IS_NULLABLE = 'NO', ' NOT NULL', ' NULL'),
               CASE
                   WHEN COLUMN_DEFAULT IS NULL THEN IF(IS_NULLABLE = 'YES', ' DEFAULT NULL', '')
                   WHEN EXTRA LIKE '%DEFAULT_GENERATED%' THEN CONCAT(' DEFAULT ', COLUMN_DEFAULT)
                   WHEN DATA_TYPE IN ('tinyint', 'smallint', 'mediumint', 'int', 'bigint',
                                      'decimal', 'numeric', 'float', 'double', 'real', 'bit')
                       THEN CONCAT(' DEFAULT ', COLUMN_DEFAULT)
                   ELSE CONCAT(' DEFAULT ', QUOTE(COLUMN_DEFAULT))
               END,
               IF(TRIM(REPLACE(EXTRA, 'DEFAULT_GENERATED', '')) = '', '',
                  CONCAT(' ', TRIM(REPLACE(EXTRA, 'DEFAULT_GENERATED', '')))),
               ' COMMENT ', QUOTE(p_column_comment)
           ) INTO v_definition
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_column_name;
    SET @sonora_profile_comment_ddl = CONCAT(
        'ALTER TABLE `', REPLACE(p_table_name, '`', '``'), '` MODIFY COLUMN ', v_definition
    );
    PREPARE sonora_profile_comment_statement FROM @sonora_profile_comment_ddl;
    EXECUTE sonora_profile_comment_statement;
    DEALLOCATE PREPARE sonora_profile_comment_statement;
END$$

DELIMITER ;

CALL sonora_set_profile_column_comment('music_behavior_event', 'playback_session_id', '跨刷新续播保持不变的客户端播放会话 UUID');
CALL sonora_set_profile_column_comment('music_behavior_event', 'session_event_key', '同一播放会话中生命周期事件的幂等键');
CALL sonora_set_profile_column_comment('music_behavior_event', 'listened_ms', '排除拖动跳转后实际累计听过的时长，单位毫秒');
CALL sonora_set_profile_column_comment('music_track_tag', 'track_key', '关联歌曲目录的稳定 SHA-256 标识');
CALL sonora_set_profile_column_comment('music_track_tag', 'tag_value', '用于展示的原始标签值');
CALL sonora_set_profile_column_comment('music_track_tag', 'normalized_value', '用于去重和聚合的标准化标签值');
CALL sonora_set_profile_column_comment('music_track_tag', 'source', '标签来源，例如 QQ 专辑、QQ 歌单或推荐上下文');
CALL sonora_set_profile_column_comment('music_track_tag', 'confidence', '标签来源可信度，范围 0 到 1');
CALL sonora_set_profile_column_comment('music_track_tag', 'first_seen_at', '标签首次获取时间');
CALL sonora_set_profile_column_comment('music_track_tag', 'updated_at', '标签最近确认或更新的时间');
CALL sonora_set_profile_column_comment('music_track_enrichment', 'track_key', '待补全标签的歌曲稳定标识');
CALL sonora_set_profile_column_comment('music_track_enrichment', 'source', '标签补全数据源标识');
CALL sonora_set_profile_column_comment('music_track_enrichment', 'source_key', '数据源中的专辑或歌曲标识');
CALL sonora_set_profile_column_comment('music_track_enrichment', 'status', '最近补全状态：SUCCESS、EMPTY 或 FAILED');
CALL sonora_set_profile_column_comment('music_track_enrichment', 'checked_at', '最近一次补全检查时间');
CALL sonora_set_profile_column_comment('music_track_enrichment', 'error_message', '最近补全失败的脱敏错误摘要');
CALL sonora_set_profile_column_comment('music_playback_session', 'id', '客户端生成的播放会话 UUID');
CALL sonora_set_profile_column_comment('music_playback_session', 'user_id', '播放会话所属用户主键');
CALL sonora_set_profile_column_comment('music_playback_session', 'track_key', '播放歌曲的稳定标识');
CALL sonora_set_profile_column_comment('music_playback_session', 'max_playback_ms', '会话到达过的最大播放位置，单位毫秒');
CALL sonora_set_profile_column_comment('music_playback_session', 'listened_ms', '会话实际累计听过的时长，单位毫秒');
CALL sonora_set_profile_column_comment('music_playback_session', 'completed', '会话是否已达到完整播放门槛');
CALL sonora_set_profile_column_comment('music_playback_session', 'started_at', '播放会话首次开始时间');
CALL sonora_set_profile_column_comment('music_playback_session', 'last_event_at', '播放会话最近一次进度或行为时间');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'user_id', '累计统计所属用户主键');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'track_key', '累计统计对应的歌曲稳定标识');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'play_count', '去重后的有效播放会话次数');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'complete_count', '达到完整播放门槛的次数');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'skip_count', '有效快速跳过次数');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'repeat_count', '主动重复播放次数');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'total_playback_ms', '排除拖动跳转后的实际累计收听时长');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'first_played_at', '用户首次播放该歌曲的时间');
CALL sonora_set_profile_column_comment('music_user_track_stat', 'last_played_at', '用户最近播放该歌曲的时间');

DROP PROCEDURE sonora_set_profile_column_comment;
