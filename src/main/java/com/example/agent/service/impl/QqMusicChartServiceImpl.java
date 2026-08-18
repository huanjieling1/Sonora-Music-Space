package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.QqChartCatalogBo;
import com.example.agent.model.bo.QqChartDetailBo;
import com.example.agent.model.bo.QqTrendReportBo;
import com.example.agent.service.QqMusicChartService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class QqMusicChartServiceImpl implements QqMusicChartService {
    private static final List<Integer> CORE_CHARTS = List.of(26, 62, 27, 4);
    private static final String METHODOLOGY =
            "Sonora 根据 QQ 音乐官方榜单名次，采用时间衰减和对数名次折扣聚合；该分数不是 QQ 官方热度分。";

    private final QqMusicSidecarClient sidecar;
    private final QqChartSnapshotRepository repository;
    private final ObjectMapper objectMapper;
    private final java.util.concurrent.ConcurrentMap<String, Instant> refreshedGroups =
            new java.util.concurrent.ConcurrentHashMap<>();

    public QqMusicChartServiceImpl(QqMusicSidecarClient sidecar,
                                   QqChartSnapshotRepository repository,
                                   ObjectMapper objectMapper) {
        this.sidecar = sidecar;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public QqChartCatalogBo catalog() {
        requireBridge();
        try {
            JsonNode response = sidecar.charts();
            List<QqChartCatalogBo.Group> groups = new ArrayList<>();
            if (response != null && response.path("groups").isArray()) {
                for (JsonNode groupNode : response.path("groups")) {
                    List<QqChartCatalogBo.Chart> charts = new ArrayList<>();
                    groupNode.path("charts").forEach(item -> charts.add(mapChart(item,
                            groupNode.path("name").asText(""))));
                    groups.add(new QqChartCatalogBo.Group(groupNode.path("name").asText(""), charts));
                }
            }
            QqChartCatalogBo result = new QqChartCatalogBo(
                    response == null ? "QQ_OFFICIAL" : response.path("sourceType").asText("QQ_OFFICIAL"),
                    instant(response == null ? null : response.path("fetchedAt").asText(null)), groups);
            repository.saveCatalog(result);
            return result;
        } catch (RestClientResponseException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐榜单目录暂时不可用");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐榜单目录暂时不可用");
        }
    }

    @Override
    public QqChartDetailBo chart(int chartId, String period, int offset, int limit) {
        if (chartId <= 0 || chartId > 10000) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐榜单标识不正确");
        }
        String resolvedPeriod = period == null ? "" : period.strip();
        if (StringUtils.hasText(resolvedPeriod) && !resolvedPeriod.matches("[0-9_-]{1,20}")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "QQ 音乐榜单周期不正确");
        }
        int resolvedOffset = Math.min(500, Math.max(0, offset));
        int resolvedLimit = Math.min(100, Math.max(1, limit));
        requireBridge();
        try {
            JsonNode response = sidecar.chart(chartId, resolvedPeriod, resolvedOffset, resolvedLimit);
            if (response == null || response.path("chart").isMissingNode()) {
                throw new AppException(HttpStatus.NOT_FOUND, "QQ 音乐榜单不存在");
            }
            QqChartCatalogBo.Chart chart = mapChart(response.path("chart"),
                    response.path("chart").path("group").asText(""));
            DateRange range = periodRange(chart.period(), chart.updateTime());
            List<QqChartDetailBo.Entry> entries = new ArrayList<>();
            if (response.path("entries").isArray()) {
                for (JsonNode item : response.path("entries")) {
                    MusicTrackBo track = chartTrack(item.path("track"), chart.name());
                    if (track != null) entries.add(new QqChartDetailBo.Entry(
                            item.path("rank").asInt(entries.size() + 1), item.path("rankType").asInt(0),
                            item.path("rankValue").asText("0"), strings(item.path("singerMids")), track));
                }
            }
            QqChartDetailBo result = new QqChartDetailBo(response.path("sourceType").asText("QQ_OFFICIAL"),
                    instant(response.path("fetchedAt").asText(null)), chart, range.start(), range.end(),
                    response.path("offset").asInt(resolvedOffset), response.path("pageSize").asInt(resolvedLimit),
                    response.path("hasNext").asBoolean(false), entries);
            repository.saveSnapshot(result);
            return result;
        } catch (RestClientResponseException exception) {
            throw new AppException(exception.getStatusCode().value() == 404 ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY,
                    exception.getStatusCode().value() == 404 ? "QQ 音乐榜单不存在" : "QQ 音乐榜单暂时不可用");
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "QQ 音乐榜单暂时不可用");
        }
    }

    @Override
    public QqTrendReportBo trendingArtists(String window, String group, int limit) {
        WindowRange range = window(window);
        refreshCurrentCharts(group);
        List<QqChartSnapshotRepository.Observation> observations = repository.observations(
                range.start(), range.end(), group, null);
        Map<String, ArtistAccumulator> artists = new LinkedHashMap<>();
        for (var observation : observations) {
            MusicTrackBo track = readTrack(observation.trackJson());
            if (track == null) continue;
            List<String> mids = readStrings(observation.singerMidsJson());
            List<String> names = readStrings(observation.singerNamesJson());
            double score = observationScore(observation.rank(), observation.periodEnd(), range.end());
            int count = Math.max(mids.size(), names.size());
            for (int index = 0; index < count; index++) {
                String mid = index < mids.size() ? mids.get(index) : "";
                String name = index < names.size() ? names.get(index) : "";
                if (!StringUtils.hasText(mid) && !StringUtils.hasText(name)) continue;
                String key = StringUtils.hasText(mid) ? mid : "name:" + name.toLowerCase(Locale.ROOT);
                artists.computeIfAbsent(key, ignored -> new ArtistAccumulator(mid, name))
                        .add(observation.songMid(), track, score, observation.rank());
            }
        }
        List<ArtistAccumulator> ranked = artists.values().stream()
                .sorted(Comparator.comparingDouble(ArtistAccumulator::score).reversed()).toList();
        double max = ranked.isEmpty() ? 1 : ranked.get(0).score();
        List<QqTrendReportBo.ArtistTrend> values = new ArrayList<>();
        for (int index = 0; index < Math.min(Math.max(1, Math.min(50, limit)), ranked.size()); index++) {
            ArtistAccumulator artist = ranked.get(index);
            values.add(artist.toTrend(index + 1, normalize(artist.score(), max)));
        }
        Coverage coverage = coverage(observations, range);
        return new QqTrendReportBo("TRENDING_ARTISTS", "近期热门歌手", range.name(),
                "SONORA_DERIVED_FROM_QQ_CHARTS", METHODOLOGY, coverage.start(), coverage.end(),
                Instant.now(), values, List.of());
    }

    @Override
    public QqTrendReportBo artistTopTracks(String artistMid, String artistName, String window, int limit) {
        if (!StringUtils.hasText(artistMid)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "缺少 QQ 音乐歌手标识");
        }
        WindowRange range = window(window);
        refreshCurrentCharts(null);
        List<QqChartSnapshotRepository.Observation> observations = repository.observations(
                range.start(), range.end(), null, artistMid.strip());
        Map<String, TrackAccumulator> tracks = new LinkedHashMap<>();
        for (var observation : observations) {
            MusicTrackBo track = readTrack(observation.trackJson());
            if (track == null) continue;
            tracks.computeIfAbsent(observation.songMid(), ignored -> new TrackAccumulator(track))
                    .add(observationScore(observation.rank(), observation.periodEnd(), range.end()),
                            observation.rank());
        }
        List<TrackAccumulator> ranked = tracks.values().stream()
                .sorted(Comparator.comparingDouble(TrackAccumulator::score).reversed()).toList();
        double max = ranked.isEmpty() ? 1 : ranked.get(0).score();
        List<QqTrendReportBo.TrackTrend> values = new ArrayList<>();
        for (int index = 0; index < Math.min(Math.max(1, Math.min(50, limit)), ranked.size()); index++) {
            TrackAccumulator track = ranked.get(index);
            values.add(track.toTrend(index + 1, normalize(track.score(), max)));
        }
        Coverage coverage = coverage(observations, range);
        String title = (StringUtils.hasText(artistName) ? artistName.strip() : "该歌手") + "的榜单热门歌曲";
        return new QqTrendReportBo("ARTIST_TOP_TRACKS", title, range.name(),
                "SONORA_DERIVED_FROM_QQ_CHARTS", METHODOLOGY, coverage.start(), coverage.end(),
                Instant.now(), List.of(), values);
    }

    @Scheduled(initialDelayString = "${music.catalog.qq.chart-initial-delay-ms:90000}",
            fixedDelayString = "${music.catalog.qq.chart-refresh-ms:3600000}")
    public void refreshCoreCharts() {
        try {
            refreshCurrentCharts(null);
        } catch (RuntimeException ignored) {
            // The bridge is optional; request-time calls retain the user-facing failure reason.
        }
    }

    private void refreshCurrentCharts(String group) {
        String refreshKey = StringUtils.hasText(group) ? group.strip() : "__core__";
        Instant previous = refreshedGroups.get(refreshKey);
        if (previous != null && previous.isAfter(Instant.now().minusSeconds(600))) return;
        synchronized (refreshedGroups) {
            previous = refreshedGroups.get(refreshKey);
            if (previous != null && previous.isAfter(Instant.now().minusSeconds(600))) return;
        QqChartCatalogBo catalog = catalog();
        List<QqChartCatalogBo.Chart> selected = catalog.groups().stream()
                .filter(value -> !StringUtils.hasText(group) || value.name().equalsIgnoreCase(group.strip()))
                .flatMap(value -> value.charts().stream())
                .filter(value -> StringUtils.hasText(group) || CORE_CHARTS.contains(value.id()))
                .limit(StringUtils.hasText(group) ? 12 : CORE_CHARTS.size())
                .toList();
        for (QqChartCatalogBo.Chart item : selected) {
            try {
                chart(item.id(), item.period(), 0, 100);
            } catch (RuntimeException ignored) {
                // One upstream chart must not prevent the other chart families from refreshing.
            }
        }
            refreshedGroups.put(refreshKey, Instant.now());
        }
    }

    private QqChartCatalogBo.Chart mapChart(JsonNode item, String group) {
        return new QqChartCatalogBo.Chart(item.path("id").asInt(), item.path("name").asText(""),
                StringUtils.hasText(item.path("group").asText()) ? item.path("group").asText() : group,
                item.path("period").asText(""), item.path("updateTime").asText(""),
                item.path("coverUrl").asText(null), item.path("description").asText(""),
                item.path("total").asInt(0));
    }

    private MusicTrackBo chartTrack(JsonNode item, String chartName) {
        String songMid = item.path("songMid").asText("");
        String name = item.path("name").asText("").strip();
        if (!songMid.matches("[A-Za-z0-9]+") || !StringUtils.hasText(name)) return null;
        String mediaMid = item.path("mediaMid").asText(songMid);
        String albumMid = item.path("albumMid").asText("");
        String imageUrl = albumMid.matches("[A-Za-z0-9]+")
                ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg?max_age=2592000"
                : null;
        String playbackUrl = UriComponentsBuilder.fromPath("/api/music/qq/play/")
                .pathSegment(songMid).queryParam("mediaId", mediaMid).build().encode().toUriString();
        return new MusicTrackBo("qq:" + songMid, name, strings(item.path("artists")),
                item.path("album").asText(""), imageUrl, Math.max(0, item.path("durationMs").asLong()),
                "https://y.qq.com/n/ryqq/songDetail/" + songMid, "qq", "audio", playbackUrl, null)
                .withAlbumId(albumMid)
                .withRecommendationReason(List.of("QQ_OFFICIAL_CHART"),
                        "QQ 音乐官方“" + chartName + "”榜单", false, 1.0);
    }

    private MusicTrackBo readTrack(String json) {
        try {
            return objectMapper.readValue(json, MusicTrackBo.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> readStrings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) node.forEach(value -> {
            if (StringUtils.hasText(value.asText())) values.add(value.asText());
        });
        return List.copyOf(values);
    }

    private static Instant instant(String value) {
        try {
            return StringUtils.hasText(value) ? Instant.parse(value) : Instant.now();
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private static DateRange periodRange(String period, String updateTime) {
        try {
            if (period != null && period.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(period);
                return new DateRange(date, date);
            }
            if (period != null && period.matches("\\d{4}_\\d{1,2}")) {
                String[] values = period.split("_");
                int year = Integer.parseInt(values[0]);
                int week = Integer.parseInt(values[1]);
                WeekFields fields = WeekFields.ISO;
                LocalDate start = LocalDate.of(year, 1, 4)
                        .with(fields.weekOfWeekBasedYear(), week).with(DayOfWeek.MONDAY);
                return new DateRange(start, start.plusDays(6));
            }
            if (StringUtils.hasText(updateTime) && updateTime.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate date = LocalDate.parse(updateTime);
                return new DateRange(date, date);
            }
        } catch (RuntimeException ignored) {
            // Fall through to today's explicit single-day coverage.
        }
        LocalDate today = LocalDate.now();
        return new DateRange(today, today);
    }

    private static WindowRange window(String value) {
        String resolved = value == null ? "RECENT" : value.strip().toUpperCase(Locale.ROOT);
        LocalDate end = LocalDate.now();
        return switch (resolved) {
            case "DAY", "DAYS" -> new WindowRange("DAY", end.minusDays(2), end);
            case "WEEK" -> new WindowRange("WEEK", end.minusDays(6), end);
            case "MONTH" -> new WindowRange("MONTH", end.minusDays(29), end);
            case "ALL_TIME" -> new WindowRange("ALL_TIME", LocalDate.of(2018, 1, 1), end);
            default -> new WindowRange("RECENT", end.minusDays(13), end);
        };
    }

    private static double observationScore(int rank, LocalDate periodEnd, LocalDate windowEnd) {
        long age = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(periodEnd, windowEnd));
        double recency = Math.pow(0.5, age / 14.0);
        return recency / (Math.log(Math.max(2, rank + 1)) / Math.log(2));
    }

    private static double normalize(double score, double max) {
        return Math.round(Math.max(0, Math.min(100, score / Math.max(0.000001, max) * 100)) * 100.0) / 100.0;
    }

    private static Coverage coverage(List<QqChartSnapshotRepository.Observation> values, WindowRange requested) {
        if (values.isEmpty()) return new Coverage(null, null);
        LocalDate start = values.stream().map(QqChartSnapshotRepository.Observation::periodStart)
                .min(LocalDate::compareTo).orElse(requested.start());
        LocalDate end = values.stream().map(QqChartSnapshotRepository.Observation::periodEnd)
                .max(LocalDate::compareTo).orElse(requested.end());
        return new Coverage(start, end);
    }

    private void requireBridge() {
        if (!sidecar.enabled() || !sidecar.healthy()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐本机 Bridge 未启动");
        }
    }

    private record DateRange(LocalDate start, LocalDate end) {}
    private record WindowRange(String name, LocalDate start, LocalDate end) {}
    private record Coverage(LocalDate start, LocalDate end) {}

    private static final class TrackAccumulator {
        private final MusicTrackBo track;
        private double score;
        private int bestRank = Integer.MAX_VALUE;
        private int appearances;

        private TrackAccumulator(MusicTrackBo track) { this.track = track; }
        private void add(double value, int rank) {
            score += value;
            bestRank = Math.min(bestRank, rank);
            appearances++;
        }
        private double score() { return score; }
        private QqTrendReportBo.TrackTrend toTrend(int rank, double normalized) {
            return new QqTrendReportBo.TrackTrend(rank, normalized,
                    bestRank == Integer.MAX_VALUE ? 0 : bestRank, appearances, track);
        }
    }

    private static final class ArtistAccumulator {
        private final String mid;
        private final String name;
        private final Map<String, TrackAccumulator> tracks = new LinkedHashMap<>();

        private ArtistAccumulator(String mid, String name) { this.mid = mid; this.name = name; }
        private void add(String songMid, MusicTrackBo track, double value, int rank) {
            tracks.computeIfAbsent(songMid, ignored -> new TrackAccumulator(track)).add(value, rank);
        }
        private double score() {
            double top = tracks.values().stream().mapToDouble(TrackAccumulator::score)
                    .boxed().sorted(Comparator.reverseOrder()).limit(3).mapToDouble(Double::doubleValue).sum();
            return top + 0.15 * Math.log1p(tracks.size());
        }
        private QqTrendReportBo.ArtistTrend toTrend(int rank, double normalized) {
            List<TrackAccumulator> top = tracks.values().stream()
                    .sorted(Comparator.comparingDouble(TrackAccumulator::score).reversed()).limit(3).toList();
            double max = top.isEmpty() ? 1 : top.get(0).score();
            List<QqTrendReportBo.TrackTrend> topTracks = new ArrayList<>();
            for (int index = 0; index < top.size(); index++) {
                topTracks.add(top.get(index).toTrend(index + 1, normalize(top.get(index).score(), max)));
            }
            int best = tracks.values().stream().mapToInt(value -> value.bestRank).min().orElse(0);
            String image = StringUtils.hasText(mid)
                    ? "https://y.gtimg.cn/music/photo_new/T001R500x500M000" + mid + ".jpg?max_age=2592000" : null;
            return new QqTrendReportBo.ArtistTrend(rank, mid, name, image, normalized, tracks.size(), best, topTracks);
        }
    }
}
