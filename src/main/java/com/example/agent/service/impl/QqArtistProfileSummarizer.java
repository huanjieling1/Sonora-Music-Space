package com.example.agent.service.impl;

import com.example.agent.model.bo.QqArtistDetailBo;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Builds conservative artist-card summaries from QQ Music's biography and catalog facts. */
public final class QqArtistProfileSummarizer {
    private static final List<String> ACHIEVEMENT_MARKERS = List.of(
            "获得", "荣获", "获奖", "奖项", "提名", "冠军", "榜单", "销量", "纪录", "代表作", "出道", "成立",
            "award", "nomination", "chart", "debut");
    private static final List<String> STYLE_MARKERS = List.of(
            "曲风", "风格", "音乐类型", "音乐特色", "擅长", "融合", "唱腔",
            "流行", "摇滚", "民谣", "电子", "爵士", "古典", "说唱", "嘻哈", "朋克", "金属", "灵魂乐",
            "r&b", "hip-hop", "pop", "rock", "folk", "electronic", "jazz", "classical", "metal");

    private QqArtistProfileSummarizer() {
    }

    public static Summary summarize(QqArtistDetailBo detail) {
        String description = normalize(detail == null ? null : detail.description());
        int songTotal = detail == null ? 0 : Math.max(0, detail.songTotal());
        int albumTotal = detail == null ? 0 : Math.max(0, detail.albumTotal());
        String catalogFact = "本次 QQ 音乐目录显示 " + songTotal + " 首歌曲、" + albumTotal + " 张专辑。";

        if (!StringUtils.hasText(description)) {
            return new Summary(
                    "QQ 音乐暂未提供可核验的艺人简介。",
                    catalogFact + " 暂无可核验的奖项或生涯成就资料。",
                    "QQ 音乐资料未提供明确曲风信息，当前不作推测。");
        }

        String biography = select(description, sentence -> true, 2, 240);
        String achievementEvidence = select(description, sentence -> containsAny(sentence, ACHIEVEMENT_MARKERS), 2, 220);
        String styleEvidence = select(description, sentence -> containsAny(sentence, STYLE_MARKERS), 2, 220);
        String achievements = StringUtils.hasText(achievementEvidence)
                ? achievementEvidence + " " + catalogFact
                : catalogFact + " QQ 音乐简介未列出可核验的具体奖项。";
        String style = StringUtils.hasText(styleEvidence)
                ? styleEvidence
                : "QQ 音乐简介未提供足以核验的曲风描述，当前不根据歌曲名或封面猜测。";
        return new Summary(biography, achievements, style);
    }

    private static String select(String description, Predicate<String> predicate, int limit, int maxLength) {
        String result = Arrays.stream(description.split("(?<=[。！？!?；;])\\s*|\\n+"))
                .map(String::strip)
                .filter(StringUtils::hasText)
                .filter(predicate)
                .limit(limit)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return compact(result, maxLength);
    }

    private static boolean containsAny(String value, List<String> markers) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return markers.stream().anyMatch(normalized::contains);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u3000', ' ').replaceAll("\\s+", " ").strip();
    }

    private static String compact(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) return value;
        return value.substring(0, maxLength).strip() + "…";
    }

    public record Summary(String biography, String achievements, String style) {
    }
}
