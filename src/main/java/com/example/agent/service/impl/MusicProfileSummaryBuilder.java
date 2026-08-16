package com.example.agent.service.impl;

import com.example.agent.model.vo.music.MusicPreferenceVo;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class MusicProfileSummaryBuilder {
    private static final int INSIGHT_LIMIT = 6;
    private static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("TRACK", "歌曲"),
            Map.entry("ARTIST", "艺人"),
            Map.entry("GENRE", "曲风"),
            Map.entry("MOOD", "情绪"),
            Map.entry("SCENE", "场景"),
            Map.entry("LANGUAGE", "语言"),
            Map.entry("TAG", "标签")
    );

    public MusicProfileSummaryVo build(List<MusicPreferenceVo> preferences, int labeledEvents, int exposures) {
        List<MusicPreferenceVo> safePreferences = preferences == null ? List.of() : List.copyOf(preferences);
        long explicitCount = safePreferences.stream().filter(item -> "L1".equals(item.layer())).count();
        long inferredCount = safePreferences.stream().filter(item -> "L2".equals(item.layer())).count();
        Stage stage = stage(safePreferences, inferredCount, labeledEvents, exposures);
        List<MusicProfileInsightVo> likes = insights(safePreferences, 1);
        List<MusicProfileInsightVo> avoids = insights(safePreferences, -1);

        String overview = overview(explicitCount, inferredCount, labeledEvents, exposures, likes, avoids);
        List<String> observations = observations(explicitCount, inferredCount, labeledEvents, exposures);
        return new MusicProfileSummaryVo(stage.name(), stage.label, stage.headline, overview,
                stage.confidenceLabel, likes, avoids, observations, LocalDateTime.now());
    }

    private static Stage stage(List<MusicPreferenceVo> preferences, long inferredCount,
                               int labeledEvents, int exposures) {
        if (preferences.isEmpty() && labeledEvents == 0) return Stage.EMPTY;
        if (labeledEvents < 3 || exposures < 2) return Stage.COLD_START;
        if (inferredCount == 0) return Stage.LEARNING;
        if (labeledEvents < 20) return Stage.FORMING;
        return Stage.STABLE;
    }

    private static List<MusicProfileInsightVo> insights(List<MusicPreferenceVo> preferences, int polarity) {
        Comparator<MusicPreferenceVo> order = Comparator
                .comparingInt((MusicPreferenceVo item) -> "L1".equals(item.layer()) ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(MusicPreferenceVo::confidence).reversed())
                .thenComparing(Comparator.comparingInt(MusicPreferenceVo::evidenceCount).reversed());
        LinkedHashMap<String, MusicPreferenceVo> unique = new LinkedHashMap<>();
        preferences.stream()
                .filter(item -> Integer.signum(item.polarity()) == polarity)
                .sorted(order)
                .forEach(item -> unique.putIfAbsent(key(item), item));
        return unique.values().stream().limit(INSIGHT_LIMIT).map(MusicProfileSummaryBuilder::insight).toList();
    }

    private static MusicProfileInsightVo insight(MusicPreferenceVo preference) {
        String type = preference.type() == null ? "TAG" : preference.type().toUpperCase(Locale.ROOT);
        String basis = "L1".equals(preference.layer())
                ? "用户明确设置"
                : "由 " + Math.max(1, preference.evidenceCount()) + " 条有效行为推断";
        return new MusicProfileInsightVo(type, TYPE_LABELS.getOrDefault(type, "偏好"), preference.value(),
                preference.polarity(), preference.layer(), bounded(preference.confidence()),
                preference.evidenceCount(), basis);
    }

    private static String overview(long explicitCount, long inferredCount, int labeledEvents, int exposures,
                                   List<MusicProfileInsightVo> likes, List<MusicProfileInsightVo> avoids) {
        if (explicitCount == 0 && inferredCount == 0 && labeledEvents == 0) {
            return "目前还没有足够的偏好或有效行为，系统不会凭空猜测你的音乐口味。";
        }
        StringBuilder result = new StringBuilder("画像基于 ")
                .append(explicitCount).append(" 条明确偏好、")
                .append(inferredCount).append(" 条有效推断，以及 ")
                .append(exposures).append(" 次推荐中的 ")
                .append(labeledEvents).append(" 条有效反馈。");
        if (!likes.isEmpty()) result.append(" 当前最明确的偏好包括：").append(values(likes)).append("。");
        if (!avoids.isEmpty()) result.append(" 已知需要避开的内容包括：").append(values(avoids)).append("。");
        return result.toString();
    }

    private static List<String> observations(long explicitCount, long inferredCount,
                                             int labeledEvents, int exposures) {
        List<String> notes = new ArrayList<>();
        if (explicitCount > 0) notes.add("明确偏好由你直接设置，优先级高于行为推断，可随时删除或修改。");
        if (inferredCount > 0) {
            notes.add("行为推断只在证据数、独立曝光数和置信度都达到门槛后生效，并会随新行为更新或过期。");
        } else if (labeledEvents > 0) {
            notes.add("已有行为证据，但尚未形成达到置信门槛的稳定推断；继续播放、喜欢、收藏或跳过会逐步完善画像。");
        } else {
            notes.add("播放完成、喜欢、收藏、重复播放、有效跳过和不喜欢会形成证据；仅曝光但没有操作不会被当作负反馈。");
        }
        if (exposures < 2) notes.add("当前独立推荐曝光较少，画像仍处于冷启动阶段，结论应谨慎看待。");
        notes.add("画像只用于有界调整排序，当前明确提出的歌曲、艺人或场景约束始终优先。");
        return List.copyOf(notes);
    }

    private static String values(List<MusicProfileInsightVo> insights) {
        return insights.stream().limit(4).map(item -> item.typeLabel() + "“" + item.value() + "”")
                .reduce((left, right) -> left + "、" + right).orElse("");
    }

    private static String key(MusicPreferenceVo preference) {
        return String.valueOf(preference.type()).toUpperCase(Locale.ROOT) + "|"
                + String.valueOf(preference.value()).strip().toLowerCase(Locale.ROOT);
    }

    private static double bounded(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private enum Stage {
        EMPTY("暂无画像", "还没有形成音乐画像", "暂无"),
        COLD_START("画像冷启动", "偏好轮廓正在建立", "较低"),
        LEARNING("学习中", "系统正在积累可靠证据", "较低"),
        FORMING("初步形成", "你的音乐偏好轮廓已初步形成", "中等"),
        STABLE("画像稳定", "你的音乐偏好画像已经较为稳定", "较高");

        private final String label;
        private final String headline;
        private final String confidenceLabel;

        Stage(String label, String headline, String confidenceLabel) {
            this.label = label;
            this.headline = headline;
            this.confidenceLabel = confidenceLabel;
        }
    }
}
