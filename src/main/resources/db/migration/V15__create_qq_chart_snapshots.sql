CREATE TABLE qq_chart_catalog (
    chart_id INT UNSIGNED NOT NULL COMMENT 'QQ 音乐官方榜单 topId',
    chart_name VARCHAR(120) NOT NULL COMMENT 'QQ 音乐榜单名称',
    chart_group VARCHAR(80) NOT NULL COMMENT '巅峰榜、地区榜、特色榜或全球榜',
    current_period VARCHAR(24) NOT NULL DEFAULT '' COMMENT '上游当前榜单周期',
    update_time VARCHAR(32) NOT NULL DEFAULT '' COMMENT '上游展示更新时间',
    cover_url VARCHAR(700) NULL COMMENT 'QQ 音乐官方榜单封面',
    description VARCHAR(1000) NOT NULL DEFAULT '' COMMENT 'QQ 音乐官方榜单说明',
    active TINYINT(1) NOT NULL DEFAULT 1 COMMENT '当前榜单目录是否仍然可见',
    refreshed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '最近一次目录刷新时间',
    PRIMARY KEY (chart_id),
    KEY idx_qq_chart_catalog_group (chart_group, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='QQ 音乐官方榜单目录缓存';

CREATE TABLE qq_chart_snapshot (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '榜单快照内部标识',
    chart_id INT UNSIGNED NOT NULL COMMENT 'QQ 音乐官方榜单 topId',
    period VARCHAR(24) NOT NULL COMMENT 'QQ 音乐官方榜单周期',
    period_start DATE NOT NULL COMMENT '该榜单周期归一化开始日期',
    period_end DATE NOT NULL COMMENT '该榜单周期归一化结束日期',
    source_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'QQ_OFFICIAL'
        COMMENT 'QQ_OFFICIAL 或其他明确数据来源',
    update_time VARCHAR(32) NOT NULL DEFAULT '' COMMENT '上游展示更新时间',
    total_count INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该周期上游歌曲总数',
    fetched_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '本机成功抓取时间',
    metadata_json JSON NULL COMMENT '榜单名称、分组、历史周期与抓取元数据',
    PRIMARY KEY (id),
    UNIQUE KEY uk_qq_chart_snapshot_period (chart_id, period),
    KEY idx_qq_chart_snapshot_window (period_end, period_start),
    KEY idx_qq_chart_snapshot_fetched (fetched_at),
    CONSTRAINT fk_qq_chart_snapshot_catalog FOREIGN KEY (chart_id)
        REFERENCES qq_chart_catalog (chart_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='QQ 音乐官方榜单不可变周期快照';

CREATE TABLE qq_chart_entry (
    snapshot_id BIGINT UNSIGNED NOT NULL COMMENT '所属榜单快照',
    rank_no INT UNSIGNED NOT NULL COMMENT '该周期官方名次',
    rank_type TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'QQ 上游原始名次变化类型',
    rank_value VARCHAR(24) NOT NULL DEFAULT '0' COMMENT 'QQ 上游原始名次变化值',
    song_mid VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'QQ 音乐 songMid',
    media_mid VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT 'QQ 音乐 mediaMid',
    song_name VARCHAR(300) NOT NULL COMMENT '歌曲名称快照',
    primary_singer_mid VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''
        COMMENT '第一位歌手 MID，便于趋势聚合',
    primary_singer_name VARCHAR(200) NOT NULL DEFAULT '' COMMENT '第一位歌手名称',
    singer_mids JSON NOT NULL COMMENT '全部歌手 MID 快照',
    singer_names JSON NOT NULL COMMENT '全部歌手名称快照',
    album_name VARCHAR(300) NOT NULL DEFAULT '' COMMENT '专辑名称快照',
    album_mid VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '' COMMENT 'QQ 音乐 albumMid',
    duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '歌曲时长毫秒',
    track_snapshot JSON NOT NULL COMMENT '可恢复的标准化歌曲快照',
    PRIMARY KEY (snapshot_id, rank_no),
    KEY idx_qq_chart_entry_song (song_mid),
    KEY idx_qq_chart_entry_artist (primary_singer_mid, snapshot_id),
    CONSTRAINT fk_qq_chart_entry_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES qq_chart_snapshot (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='QQ 音乐官方榜单歌曲名次';
