-- 为既有字段安全补充注释：从 information_schema 重建原字段定义，避免改变类型、
-- 字符集、排序规则、空值、默认值、自增或 ON UPDATE 属性。
DELIMITER $$

CREATE PROCEDURE sonora_set_column_comment(
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
           )
      INTO v_definition
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_table_name
       AND COLUMN_NAME = p_column_name;

    IF v_definition IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot document a missing database column';
    END IF;

    SET @sonora_comment_ddl = CONCAT(
        'ALTER TABLE `', REPLACE(p_table_name, '`', '``'), '` MODIFY COLUMN ', v_definition
    );
    PREPARE sonora_comment_statement FROM @sonora_comment_ddl;
    EXECUTE sonora_comment_statement;
    DEALLOCATE PREPARE sonora_comment_statement;
END$$

DELIMITER ;

ALTER TABLE flyway_schema_history COMMENT = 'Flyway 数据库迁移版本与执行历史';
CALL sonora_set_column_comment('flyway_schema_history', 'installed_rank', '迁移执行顺序主键');
CALL sonora_set_column_comment('flyway_schema_history', 'version', '迁移版本号');
CALL sonora_set_column_comment('flyway_schema_history', 'description', '迁移描述');
CALL sonora_set_column_comment('flyway_schema_history', 'type', '迁移脚本类型');
CALL sonora_set_column_comment('flyway_schema_history', 'script', '迁移脚本名称');
CALL sonora_set_column_comment('flyway_schema_history', 'checksum', '迁移脚本校验和');
CALL sonora_set_column_comment('flyway_schema_history', 'installed_by', '执行迁移的数据库账号');
CALL sonora_set_column_comment('flyway_schema_history', 'installed_on', '迁移安装时间');
CALL sonora_set_column_comment('flyway_schema_history', 'execution_time', '迁移执行耗时，单位毫秒');
CALL sonora_set_column_comment('flyway_schema_history', 'success', '迁移是否执行成功');

CALL sonora_set_column_comment('music_behavior_event', 'id', '行为事件数据库自增主键');
CALL sonora_set_column_comment('music_behavior_event', 'event_id', '客户端生成的事件幂等 UUID');
CALL sonora_set_column_comment('music_behavior_event', 'user_id', '行为所属用户主键');
CALL sonora_set_column_comment('music_behavior_event', 'exposure_id', '行为引用的推荐曝光 UUID');
CALL sonora_set_column_comment('music_behavior_event', 'recommendation_item_id', '行为引用的曝光歌曲快照主键');
CALL sonora_set_column_comment('music_behavior_event', 'event_type', '播放、跳过、播完、喜欢或收藏等事件类型');
CALL sonora_set_column_comment('music_behavior_event', 'playback_ms', '事件发生时的有效播放时长，单位毫秒');
CALL sonora_set_column_comment('music_behavior_event', 'reward', '用于离线排序学习的明确反馈分值');
CALL sonora_set_column_comment('music_behavior_event', 'created_at', '行为事件创建时间');

CALL sonora_set_column_comment('music_catalog_track', 'provider', '歌曲所属在线曲库标识');
CALL sonora_set_column_comment('music_catalog_track', 'provider_track_id', '歌曲在对应曲库中的原始标识');
CALL sonora_set_column_comment('music_catalog_track', 'title', '标准化歌曲标题');
CALL sonora_set_column_comment('music_catalog_track', 'primary_artist', '主要艺人名称');
CALL sonora_set_column_comment('music_catalog_track', 'album', '专辑名称');
CALL sonora_set_column_comment('music_catalog_track', 'content_text', '用于语义向量化的标准化元数据文本');
CALL sonora_set_column_comment('music_catalog_track', 'content_hash', '元数据内容 SHA-256 哈希');
CALL sonora_set_column_comment('music_catalog_track', 'metadata_json', '可重建的完整歌曲元数据 JSON');
CALL sonora_set_column_comment('music_catalog_track', 'embedding_model', '生成当前向量的嵌入模型名称');
CALL sonora_set_column_comment('music_catalog_track', 'embedding_dimensions', '歌曲向量维度');
CALL sonora_set_column_comment('music_catalog_track', 'graph_projected_hash', '最近成功投影到 Neo4j 的内容哈希');
CALL sonora_set_column_comment('music_catalog_track', 'embedding_content_hash', '最近成功生成向量的内容哈希');
CALL sonora_set_column_comment('music_catalog_track', 'first_seen_at', '歌曲首次进入目录时间');
CALL sonora_set_column_comment('music_catalog_track', 'last_seen_at', '歌曲最近一次出现在候选中的时间');

CALL sonora_set_column_comment('music_graph_outbox', 'id', '投影任务自增主键');
CALL sonora_set_column_comment('music_graph_outbox', 'user_id', '关联用户主键，歌曲公共投影可为空');
CALL sonora_set_column_comment('music_graph_outbox', 'aggregate_type', '待投影聚合根类型');
CALL sonora_set_column_comment('music_graph_outbox', 'aggregate_id', '待投影聚合根业务标识');
CALL sonora_set_column_comment('music_graph_outbox', 'event_type', 'Neo4j 投影事件类型');
CALL sonora_set_column_comment('music_graph_outbox', 'payload', '投影所需的完整事实数据 JSON');
CALL sonora_set_column_comment('music_graph_outbox', 'status', '任务状态：PENDING、RETRY 或 DONE');
CALL sonora_set_column_comment('music_graph_outbox', 'attempts', '投影累计尝试次数');
CALL sonora_set_column_comment('music_graph_outbox', 'next_attempt_at', '失败任务下次允许重试时间');
CALL sonora_set_column_comment('music_graph_outbox', 'last_error', '最近一次失败的脱敏错误摘要');
CALL sonora_set_column_comment('music_graph_outbox', 'created_at', '投影任务创建时间');
CALL sonora_set_column_comment('music_graph_outbox', 'processed_at', '投影成功完成时间');

CALL sonora_set_column_comment('music_knowledge_alias', 'id', '音乐实体别名自增主键');
CALL sonora_set_column_comment('music_knowledge_alias', 'entity_id', '别名所属音乐实体主键');
CALL sonora_set_column_comment('music_knowledge_alias', 'alias_name', '实体原始别名');
CALL sonora_set_column_comment('music_knowledge_alias', 'normalized_alias', '用于匹配的标准化别名');
CALL sonora_set_column_comment('music_knowledge_alias', 'source', '别名数据来源');
CALL sonora_set_column_comment('music_knowledge_alias', 'priority', '别名匹配优先级，数值越小优先级越高');
CALL sonora_set_column_comment('music_knowledge_alias', 'created_at', '别名创建时间');

CALL sonora_set_column_comment('music_knowledge_cache', 'cache_key', '外部音乐知识查询缓存键');
CALL sonora_set_column_comment('music_knowledge_cache', 'provider', '知识数据提供方标识');
CALL sonora_set_column_comment('music_knowledge_cache', 'successful', '外部知识查询是否成功');
CALL sonora_set_column_comment('music_knowledge_cache', 'payload', '外部知识查询响应或失败信息');
CALL sonora_set_column_comment('music_knowledge_cache', 'expires_at', '缓存失效时间');
CALL sonora_set_column_comment('music_knowledge_cache', 'created_at', '缓存创建时间');
CALL sonora_set_column_comment('music_knowledge_cache', 'updated_at', '缓存最近更新时间');

CALL sonora_set_column_comment('music_knowledge_entity', 'id', '音乐知识实体自增主键');
CALL sonora_set_column_comment('music_knowledge_entity', 'canonical_name', '音乐实体规范名称');
CALL sonora_set_column_comment('music_knowledge_entity', 'normalized_name', '用于检索匹配的标准化名称');
CALL sonora_set_column_comment('music_knowledge_entity', 'entity_type', '歌曲、艺人、专辑或作品系列等实体类型');
CALL sonora_set_column_comment('music_knowledge_entity', 'parent_entity_id', '上级音乐实体主键');
CALL sonora_set_column_comment('music_knowledge_entity', 'related_terms', '辅助召回的相关词集合');
CALL sonora_set_column_comment('music_knowledge_entity', 'source', '实体知识来源');
CALL sonora_set_column_comment('music_knowledge_entity', 'source_ref', '来源系统中的实体引用');
CALL sonora_set_column_comment('music_knowledge_entity', 'confidence', '实体知识置信度');
CALL sonora_set_column_comment('music_knowledge_entity', 'created_at', '实体创建时间');
CALL sonora_set_column_comment('music_knowledge_entity', 'updated_at', '实体最近更新时间');

CALL sonora_set_column_comment('music_knowledge_feedback', 'id', '音乐知识反馈自增主键');
CALL sonora_set_column_comment('music_knowledge_feedback', 'user_id', '反馈所属用户主键');
CALL sonora_set_column_comment('music_knowledge_feedback', 'conversation_id', '反馈生效的 Agent 会话 UUID');
CALL sonora_set_column_comment('music_knowledge_feedback', 'search_id', '反馈引用的推荐曝光 UUID');
CALL sonora_set_column_comment('music_knowledge_feedback', 'action', '实体纠错或场景不相关等反馈动作');
CALL sonora_set_column_comment('music_knowledge_feedback', 'description', '产生反馈时的原始音乐需求');
CALL sonora_set_column_comment('music_knowledge_feedback', 'normalized_description', '用于聚合的标准化需求描述');
CALL sonora_set_column_comment('music_knowledge_feedback', 'track_id', '反馈涉及的曲库歌曲标识');
CALL sonora_set_column_comment('music_knowledge_feedback', 'resolved_entity_name', '系统原先解析出的实体名称');
CALL sonora_set_column_comment('music_knowledge_feedback', 'corrected_entity_name', '用户纠正后的实体名称');
CALL sonora_set_column_comment('music_knowledge_feedback', 'corrected_entity_type', '用户纠正后的实体类型');
CALL sonora_set_column_comment('music_knowledge_feedback', 'created_at', '反馈创建时间');

CALL sonora_set_column_comment('music_knowledge_track_relation', 'id', '实体歌曲关系自增主键');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'entity_id', '关系所属音乐实体主键');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'track_title', '关系中的歌曲原始标题');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'normalized_track_title', '用于匹配的标准化歌曲标题');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'artist_name', '关系中的艺人名称');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'normalized_artist_name', '用于匹配的标准化艺人名称');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'album_name', '关系中的专辑名称');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'relation_type', '主题曲、插曲或相关作品等关系类型');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'relation_label', '用于页面展示的关系说明');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'source', '关系知识来源');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'source_url', '关系知识来源页面地址');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'confidence', '实体歌曲关系置信度');
CALL sonora_set_column_comment('music_knowledge_track_relation', 'created_at', '关系创建时间');

CALL sonora_set_column_comment('music_playlist', 'id', '歌单 UUID 主键');
CALL sonora_set_column_comment('music_playlist', 'user_id', '歌单所属用户主键');
CALL sonora_set_column_comment('music_playlist', 'playlist_type', '自建、推荐、喜欢或最近播放等歌单类型');
CALL sonora_set_column_comment('music_playlist', 'system_key', '同一用户系统歌单的唯一键');
CALL sonora_set_column_comment('music_playlist', 'name', '歌单名称');
CALL sonora_set_column_comment('music_playlist', 'description', '歌单简介');
CALL sonora_set_column_comment('music_playlist', 'cover_url', '歌单封面图片地址');
CALL sonora_set_column_comment('music_playlist', 'source_description', '生成推荐歌单时的原始需求描述');
CALL sonora_set_column_comment('music_playlist', 'source_exposure_id', '生成歌单所依据的推荐曝光 UUID');
CALL sonora_set_column_comment('music_playlist', 'policy_version', '生成推荐歌单时使用的排序策略版本');
CALL sonora_set_column_comment('music_playlist', 'editable', '用户是否可以编辑或删除该歌单');
CALL sonora_set_column_comment('music_playlist', 'created_at', '歌单创建时间');
CALL sonora_set_column_comment('music_playlist', 'updated_at', '歌单最近更新时间');

CALL sonora_set_column_comment('music_playlist_track', 'id', '歌单歌曲自增主键');
CALL sonora_set_column_comment('music_playlist_track', 'playlist_id', '所属歌单 UUID');
CALL sonora_set_column_comment('music_playlist_track', 'track_key', '跨曲库稳定歌曲 SHA-256 标识');
CALL sonora_set_column_comment('music_playlist_track', 'position', '歌曲在歌单中的显示顺序');
CALL sonora_set_column_comment('music_playlist_track', 'track_snapshot', '加入歌单时的完整歌曲元数据 JSON');
CALL sonora_set_column_comment('music_playlist_track', 'added_at', '歌曲加入歌单时间');

CALL sonora_set_column_comment('music_preference_memory', 'id', '偏好记忆 UUID 主键');
CALL sonora_set_column_comment('music_preference_memory', 'user_id', '偏好记忆所属用户主键');
CALL sonora_set_column_comment('music_preference_memory', 'scope_id', '会话范围记忆对应的会话 UUID');
CALL sonora_set_column_comment('music_preference_memory', 'preference_type', '风格、情绪、场景、语言、艺人或歌曲等偏好类型');
CALL sonora_set_column_comment('music_preference_memory', 'preference_value', '偏好原始展示值');
CALL sonora_set_column_comment('music_preference_memory', 'normalized_value', '用于匹配和去重的标准化偏好值');
CALL sonora_set_column_comment('music_preference_memory', 'confidence', '偏好置信度');
CALL sonora_set_column_comment('music_preference_memory', 'evidence_count', '支持该偏好的有效行为事件数量');
CALL sonora_set_column_comment('music_preference_memory', 'distinct_exposures', '支持该偏好的不同曝光数量');
CALL sonora_set_column_comment('music_preference_memory', 'source', '用户填写或行为推断等记忆来源');
CALL sonora_set_column_comment('music_preference_memory', 'valid_from', '偏好开始生效时间');
CALL sonora_set_column_comment('music_preference_memory', 'expires_at', '偏好自动失效时间，永久记忆为空');
CALL sonora_set_column_comment('music_preference_memory', 'deleted_at', '偏好逻辑删除时间');
CALL sonora_set_column_comment('music_preference_memory', 'superseded_by', '取代当前记忆的新记忆 UUID');
CALL sonora_set_column_comment('music_preference_memory', 'created_at', '偏好记忆创建时间');
CALL sonora_set_column_comment('music_preference_memory', 'updated_at', '偏好记忆最近更新时间');

CALL sonora_set_column_comment('music_rank_policy', 'version', '排序策略唯一版本号');
CALL sonora_set_column_comment('music_rank_policy', 'status', '候选、验证通过或验证失败等策略状态');
CALL sonora_set_column_comment('music_rank_policy', 'coefficients', '排序特征权重 JSON');
CALL sonora_set_column_comment('music_rank_policy', 'metrics', '离线验证指标与分段守卫结果 JSON');
CALL sonora_set_column_comment('music_rank_policy', 'training_started_at', '策略训练开始时间');
CALL sonora_set_column_comment('music_rank_policy', 'training_ended_at', '策略训练结束时间');
CALL sonora_set_column_comment('music_rank_policy', 'labeled_events', '训练使用的明确标签事件数量');
CALL sonora_set_column_comment('music_rank_policy', 'exposures', '训练使用的推荐曝光数量');
CALL sonora_set_column_comment('music_rank_policy', 'created_at', '策略版本创建时间');

CALL sonora_set_column_comment('music_recommendation_exposure', 'id', '推荐曝光 UUID，同时作为 searchId');
CALL sonora_set_column_comment('music_recommendation_exposure', 'user_id', '曝光所属用户主键');
CALL sonora_set_column_comment('music_recommendation_exposure', 'conversation_id', '曝光所属 Agent 会话 UUID');
CALL sonora_set_column_comment('music_recommendation_exposure', 'description', '产生该曝光的原始音乐需求');
CALL sonora_set_column_comment('music_recommendation_exposure', 'plan_json', '结构化音乐执行计划 JSON');
CALL sonora_set_column_comment('music_recommendation_exposure', 'policy_version', '曝光使用的排序策略版本');
CALL sonora_set_column_comment('music_recommendation_exposure', 'personalization_status', '曝光时的个性化启用或降级状态');
CALL sonora_set_column_comment('music_recommendation_exposure', 'created_at', '曝光创建时间');

CALL sonora_set_column_comment('music_recommendation_item', 'id', '曝光歌曲快照自增主键');
CALL sonora_set_column_comment('music_recommendation_item', 'exposure_id', '所属推荐曝光 UUID');
CALL sonora_set_column_comment('music_recommendation_item', 'track_key', '跨曲库稳定歌曲 SHA-256 标识');
CALL sonora_set_column_comment('music_recommendation_item', 'provider', '歌曲所属在线曲库标识');
CALL sonora_set_column_comment('music_recommendation_item', 'provider_track_id', '歌曲在对应曲库中的原始标识');
CALL sonora_set_column_comment('music_recommendation_item', 'display_position', '歌曲在本次曝光中的展示位置');
CALL sonora_set_column_comment('music_recommendation_item', 'track_snapshot', '曝光时的完整歌曲数据 JSON');
CALL sonora_set_column_comment('music_recommendation_item', 'source_ranks', '各召回通道中的原始排名 JSON');
CALL sonora_set_column_comment('music_recommendation_item', 'feature_snapshot', '排序特征与用户调整快照 JSON');
CALL sonora_set_column_comment('music_recommendation_item', 'final_score', '融合、个性化和重排后的最终分数');
CALL sonora_set_column_comment('music_recommendation_item', 'reason_codes', '推荐理由代码集合 JSON');
CALL sonora_set_column_comment('music_recommendation_item', 'exploration', '该歌曲是否占用探索推荐位');
CALL sonora_set_column_comment('music_recommendation_item', 'created_at', '曝光歌曲快照创建时间');

DROP PROCEDURE sonora_set_column_comment;

