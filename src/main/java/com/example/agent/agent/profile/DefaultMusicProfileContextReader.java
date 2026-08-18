package com.example.agent.agent.profile;

import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.model.vo.music.MusicProfileAnalyticsVo;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import com.example.agent.service.MusicPersonalizationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Component
public class DefaultMusicProfileContextReader implements MusicProfileContextReader {
    private final MusicPersonalizationService personalizationService;

    public DefaultMusicProfileContextReader(MusicPersonalizationService personalizationService) {
        this.personalizationService = personalizationService;
    }

    @Override
    public UserTasteContext read(long userId) {
        MusicProfileVo profile = personalizationService.profile(userId);
        MusicProfileSummaryVo summary = profile == null ? null : profile.summary();
        MusicProfileAnalyticsVo analytics = profile == null ? null : profile.analytics();
        return new UserTasteContext(
                summary == null ? "EMPTY" : summary.stage(),
                summary == null ? "暂无画像" : summary.stageLabel(),
                analytics != null && analytics.profileReady(),
                analytics == null ? 0 : analytics.playCount(),
                analytics == null ? 0 : analytics.uniqueTracks(),
                analytics == null ? 0 : analytics.totalPlaybackMs(),
                analytics == null ? 0 : analytics.completionRate(),
                signals(summary == null ? List.of() : summary.likes(), "like"),
                signals(summary == null ? List.of() : summary.avoids(), "avoid"),
                labels(analytics),
                tracks(analytics),
                artists(analytics),
                tags(analytics),
                summary == null ? List.of("当前还没有可验证的音乐画像。") : summary.observations());
    }

    private static List<UserTasteContext.Signal> signals(List<MusicProfileInsightVo> source, String prefix) {
        if (source == null) return List.of();
        return IntStream.range(0, source.size()).mapToObj(index -> {
            MusicProfileInsightVo value = source.get(index);
            return new UserTasteContext.Signal(value.type(), value.value(), value.basis(), value.confidence(),
                    prefix + ":" + index + ":" + normalized(value.value()));
        }).toList();
    }

    private static List<UserTasteContext.Signal> labels(MusicProfileAnalyticsVo analytics) {
        if (analytics == null) return List.of();
        return IntStream.range(0, analytics.labels().size()).mapToObj(index -> {
            MusicProfileAnalyticsVo.Label value = analytics.labels().get(index);
            return new UserTasteContext.Signal("USER_LABEL", value.name(), value.basis(), value.confidence(),
                    "label:" + value.code().toLowerCase(Locale.ROOT));
        }).toList();
    }

    private static List<UserTasteContext.RankedItem> tracks(MusicProfileAnalyticsVo analytics) {
        if (analytics == null) return List.of();
        return analytics.topTracks().stream().limit(5).map(value -> new UserTasteContext.RankedItem(
                value.title(), value.artist(), value.playCount(), "track:" + value.trackKey())).toList();
    }

    private static List<UserTasteContext.RankedItem> artists(MusicProfileAnalyticsVo analytics) {
        if (analytics == null) return List.of();
        return IntStream.range(0, Math.min(5, analytics.topArtists().size())).mapToObj(index -> {
            MusicProfileAnalyticsVo.Artist value = analytics.topArtists().get(index);
            return new UserTasteContext.RankedItem(value.name(), value.uniqueTracks() + " 首歌曲",
                    value.playCount(), "artist:" + index + ":" + normalized(value.name()));
        }).toList();
    }

    private static List<UserTasteContext.RankedItem> tags(MusicProfileAnalyticsVo analytics) {
        if (analytics == null) return List.of();
        return IntStream.range(0, Math.min(10, analytics.topTags().size())).mapToObj(index -> {
            MusicProfileAnalyticsVo.Tag value = analytics.topTags().get(index);
            return new UserTasteContext.RankedItem(value.value(), value.type() + " · 可信度 "
                    + Math.round(value.confidence() * 100) + "%", value.playCount(),
                    "tag:" + value.type().toLowerCase(Locale.ROOT) + ":" + normalized(value.value()));
        }).toList();
    }

    private static String normalized(String value) {
        return value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-");
    }
}
