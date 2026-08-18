package com.example.agent.service.impl;

import com.example.agent.model.bo.QqChartCatalogBo;
import com.example.agent.model.bo.QqChartDetailBo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/** Persists immutable QQ chart periods so multi-day trends have an auditable source. */
@Repository
public class QqChartSnapshotRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public QqChartSnapshotRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveCatalog(QqChartCatalogBo catalog) {
        jdbc.update("UPDATE qq_chart_catalog SET active = 0");
        for (QqChartCatalogBo.Group group : catalog.groups()) {
            for (QqChartCatalogBo.Chart chart : group.charts()) {
                jdbc.update("""
                        INSERT INTO qq_chart_catalog
                            (chart_id, chart_name, chart_group, current_period, update_time,
                             cover_url, description, active)
                        VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                        ON DUPLICATE KEY UPDATE chart_name = VALUES(chart_name),
                            chart_group = VALUES(chart_group), current_period = VALUES(current_period),
                            update_time = VALUES(update_time), cover_url = VALUES(cover_url),
                            description = VALUES(description), active = 1
                        """, chart.id(), chart.name(), group.name(), chart.period(), chart.updateTime(),
                        chart.coverUrl(), chart.description());
            }
        }
    }

    @Transactional
    public void saveSnapshot(QqChartDetailBo detail) {
        QqChartCatalogBo.Chart chart = detail.chart();
        jdbc.update("""
                INSERT INTO qq_chart_catalog
                    (chart_id, chart_name, chart_group, current_period, update_time,
                     cover_url, description, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                ON DUPLICATE KEY UPDATE chart_name = VALUES(chart_name),
                    chart_group = VALUES(chart_group), current_period = VALUES(current_period),
                    update_time = VALUES(update_time), cover_url = VALUES(cover_url),
                    description = VALUES(description), active = 1
                """, chart.id(), chart.name(), chart.group(), chart.period(), chart.updateTime(),
                chart.coverUrl(), chart.description());
        jdbc.update("""
                INSERT INTO qq_chart_snapshot
                    (chart_id, period, period_start, period_end, source_type, update_time,
                     total_count, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                ON DUPLICATE KEY UPDATE period_start = VALUES(period_start), period_end = VALUES(period_end),
                    update_time = VALUES(update_time), total_count = VALUES(total_count),
                    fetched_at = CURRENT_TIMESTAMP(6), metadata_json = VALUES(metadata_json)
                """, chart.id(), chart.period(), Date.valueOf(detail.coverageStart()),
                Date.valueOf(detail.coverageEnd()), detail.sourceType(), chart.updateTime(), chart.total(),
                json(chart));
        Long snapshotId = jdbc.queryForObject("""
                SELECT id FROM qq_chart_snapshot WHERE chart_id = ? AND period = ?
                """, Long.class, chart.id(), chart.period());
        if (snapshotId == null) return;
        jdbc.update("DELETE FROM qq_chart_entry WHERE snapshot_id = ?", snapshotId);
        for (QqChartDetailBo.Entry entry : detail.entries()) {
            var track = entry.track();
            String primaryMid = entry.singerMids().isEmpty() ? "" : entry.singerMids().get(0);
            String primaryName = track.artists().isEmpty() ? "" : track.artists().get(0);
            jdbc.update("""
                    INSERT INTO qq_chart_entry
                        (snapshot_id, rank_no, rank_type, rank_value, song_mid, media_mid,
                         song_name, primary_singer_mid, primary_singer_name, singer_mids,
                         singer_names, album_name, album_mid, duration_ms, track_snapshot)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON),
                            ?, ?, ?, CAST(? AS JSON))
                    """, snapshotId, entry.rank(), entry.rankType(), entry.rankValue(),
                    stripProvider(track.id()), mediaId(track.playbackUrl()), track.name(), primaryMid,
                    primaryName, json(entry.singerMids()), json(track.artists()), track.album(),
                    track.albumId() == null ? "" : track.albumId(), track.durationMs(), json(track));
        }
    }

    public List<Observation> observations(LocalDate start, LocalDate end, String group, String artistMid) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.chart_id, c.chart_name, c.chart_group, s.period, s.period_start, s.period_end,
                       e.rank_no, e.song_mid, e.primary_singer_mid, e.primary_singer_name,
                       e.singer_mids, e.singer_names, e.track_snapshot
                  FROM qq_chart_snapshot s
                  JOIN qq_chart_catalog c ON c.chart_id = s.chart_id
                  JOIN qq_chart_entry e ON e.snapshot_id = s.id
                 WHERE s.period_end >= ? AND s.period_start <= ?
                """);
        java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();
        parameters.add(Date.valueOf(start));
        parameters.add(Date.valueOf(end));
        if (StringUtils.hasText(group)) {
            sql.append(" AND c.chart_group = ?");
            parameters.add(group.strip());
        }
        if (StringUtils.hasText(artistMid)) {
            sql.append(" AND JSON_CONTAINS(e.singer_mids, JSON_QUOTE(?))");
            parameters.add(artistMid.strip());
        }
        sql.append(" ORDER BY s.period_end DESC, s.chart_id, e.rank_no LIMIT 50000");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new Observation(
                rs.getInt("chart_id"), rs.getString("chart_name"), rs.getString("chart_group"),
                rs.getString("period"), rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(), rs.getInt("rank_no"),
                rs.getString("song_mid"), rs.getString("primary_singer_mid"),
                rs.getString("primary_singer_name"), rs.getString("singer_mids"),
                rs.getString("singer_names"), rs.getString("track_snapshot")),
                parameters.toArray());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 QQ 榜单快照", exception);
        }
    }

    private static String stripProvider(String id) {
        return id != null && id.startsWith("qq:") ? id.substring(3) : id;
    }

    private static String mediaId(String playbackUrl) {
        if (!StringUtils.hasText(playbackUrl)) return "";
        int index = playbackUrl.indexOf("mediaId=");
        if (index < 0) return "";
        String value = playbackUrl.substring(index + 8);
        int separator = value.indexOf('&');
        return separator < 0 ? value : value.substring(0, separator);
    }

    public record Observation(int chartId, String chartName, String chartGroup, String period,
                              LocalDate periodStart, LocalDate periodEnd, int rank, String songMid,
                              String artistMid, String artistName, String singerMidsJson,
                              String singerNamesJson, String trackJson) {
    }
}
