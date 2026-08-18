package com.example.agent.service.impl;

import com.example.agent.model.vo.music.MusicProfileAnalyticsVo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class MusicProfileAnalyticsBuilder {
    static final int REQUIRED_PLAYS = 20;
    static final int REQUIRED_UNIQUE_TRACKS = 8;

    public MusicProfileAnalyticsVo build(MusicPersonalizationRepository.ListeningTotals totals,
                                         List<MusicPersonalizationRepository.TrackStatRow> tracks,
                                         List<MusicPersonalizationRepository.ArtistStatRow> artists,
                                         List<MusicPersonalizationRepository.TagStatRow> tags) {
        long plays = totals == null ? 0 : totals.plays();
        long uniqueTracks = totals == null ? 0 : totals.uniqueTracks();
        boolean ready = plays >= REQUIRED_PLAYS && uniqueTracks >= REQUIRED_UNIQUE_TRACKS;

        List<MusicProfileAnalyticsVo.Track> topTracks = tracks.stream().map(row ->
                new MusicProfileAnalyticsVo.Track(row.trackKey(), row.provider(), row.trackId(), row.title(),
                        row.artist(), row.album(), row.plays(), row.completes(), row.skips(), row.repeats(),
                        row.playbackMs(), row.lastPlayedAt())).toList();
        List<MusicProfileAnalyticsVo.Artist> topArtists = artists.stream().map(row ->
                new MusicProfileAnalyticsVo.Artist(row.artist(), row.uniqueTracks(), row.plays(),
                        row.completes(), row.repeats(), row.playbackMs(), ratio(row.plays(), plays),
                        row.lastPlayedAt())).toList();
        List<MusicProfileAnalyticsVo.Tag> topTags = tags.stream().map(row ->
                new MusicProfileAnalyticsVo.Tag(row.type(), row.value(), row.uniqueTracks(), row.plays(),
                        row.playbackMs(), row.affinity(), bounded(row.confidence()),
                        ratio(row.plays(), plays))).toList();

        List<MusicProfileAnalyticsVo.Label> labels = ready
                ? labels(totals, topArtists, topTags) : List.of();
        return new MusicProfileAnalyticsVo(uniqueTracks, plays,
                totals == null ? 0 : totals.completes(), totals == null ? 0 : totals.skips(),
                totals == null ? 0 : totals.repeats(), totals == null ? 0 : totals.playbackMs(),
                ratio(totals == null ? 0 : totals.completes(), plays), ready,
                REQUIRED_PLAYS, REQUIRED_UNIQUE_TRACKS, topTracks, topArtists, topTags, labels,
                totals == null ? null : totals.firstPlayedAt(), totals == null ? null : totals.lastPlayedAt(),
                LocalDateTime.now());
    }

    private static List<MusicProfileAnalyticsVo.Label> labels(
            MusicPersonalizationRepository.ListeningTotals totals,
            List<MusicProfileAnalyticsVo.Artist> artists,
            List<MusicProfileAnalyticsVo.Tag> tags) {
        List<MusicProfileAnalyticsVo.Label> result = new ArrayList<>();
        if (!artists.isEmpty()) {
            var artist = artists.get(0);
            if (artist.playCount() >= 8 && artist.playShare() >= 0.20) {
                result.add(new MusicProfileAnalyticsVo.Label("ARTIST_LOYALTY",
                        artist.name() + "深度听众",
                        "累计播放 " + artist.playCount() + " 次，占有效播放 " + percent(artist.playShare()),
                        bounded(0.65 + Math.min(0.3, artist.playShare()))));
            }
        }
        if (!tags.isEmpty()) {
            var tag = tags.get(0);
            if (tag.uniqueTracks() >= 4 && tag.playShare() >= 0.25) {
                result.add(new MusicProfileAnalyticsVo.Label("TAG_AFFINITY",
                        tag.value() + "偏爱者",
                        "该标签覆盖 " + tag.uniqueTracks() + " 首听过的歌曲，播放占比 " + percent(tag.playShare()),
                        bounded(tag.confidence() * 0.75 + Math.min(0.2, tag.playShare()))));
            }
        }
        double repeatRate = ratio(totals.repeats(), totals.plays());
        if (totals.repeats() >= 5 && repeatRate >= 0.20) {
            result.add(new MusicProfileAnalyticsVo.Label("REPEAT_LISTENER", "单曲循环型听众",
                    "重复播放 " + totals.repeats() + " 次，占播放会话 " + percent(repeatRate),
                    bounded(0.65 + repeatRate)));
        }
        double completionRate = ratio(totals.completes(), totals.plays());
        if (completionRate >= 0.75) {
            result.add(new MusicProfileAnalyticsVo.Label("HIGH_COMPLETION", "高完播型听众",
                    "完整听完比例达到 " + percent(completionRate), bounded(completionRate)));
        }
        double explorationRate = ratio(totals.uniqueTracks(), totals.plays());
        if (totals.uniqueTracks() >= 15 && explorationRate >= 0.50) {
            result.add(new MusicProfileAnalyticsVo.Label("EXPLORER", "新歌探索者",
                    "听过 " + totals.uniqueTracks() + " 首不同歌曲，多样性比例 " + percent(explorationRate),
                    bounded(0.6 + explorationRate * 0.35)));
        }
        return List.copyOf(result.stream().limit(6).toList());
    }

    private static double ratio(long value, long total) {
        return total <= 0 ? 0 : bounded((double) value / total);
    }

    private static double bounded(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static String percent(double value) {
        return Math.round(bounded(value) * 100) + "%";
    }
}
