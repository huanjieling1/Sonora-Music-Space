package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicHardConstraints;
import com.example.agent.model.bo.MusicIntentHints;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicSoftIntent;
import com.example.agent.model.bo.MusicToolCall;
import com.example.agent.model.bo.MusicToolName;
import com.example.agent.model.bo.MusicUnderstandingBo;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts model output into a small, closed and deterministic provider execution plan. */
@Component
public class MusicSearchPlanCompiler {
    private static final int MAX_EXPANSIONS = 3;
    private static final Pattern AVOID_PATTERN = Pattern.compile(
            "(?:不要|避开|排除|不想听|别放|除了)\\s*([^，。；;,.]{1,40})");
    private static final Map<String, List<String>> GUARDED_EXPANSIONS = Map.of(
            "official", List.of("official", "官方"),
            "soundtrack", List.of("soundtrack", "ost", "原声", "原声带", "主题曲"),
            "anthem", List.of("anthem", "主题曲", "会歌"),
            "champion", List.of("champion", "champions", "冠军", "冠军赛")
    );

    public MusicExecutionPlan compile(String description, MusicSearchPlan proposed) {
        String original = MusicTextNormalizer.cleanRequest(description);
        MusicSearchPlan plan = proposed == null
                ? new MusicSearchPlan(MusicSearchIntent.AMBIGUOUS, null, List.of(), null,
                List.of(), List.of(), List.of(), List.of(), 0, "请补充歌曲、歌手、专辑或想听的氛围。")
                : proposed;
        MusicSearchIntent intent = plan.intent() == null ? MusicSearchIntent.AMBIGUOUS : plan.intent();
        List<String> artists = cleanTerms(plan.artists(), 5);
        MusicHardConstraints hard = new MusicHardConstraints(
                clean(plan.track(), 80), artists, clean(plan.album(), 80));
        MusicSoftIntent soft = new MusicSoftIntent(
                intent == MusicSearchIntent.DISCOVERY || intent == MusicSearchIntent.SIMILAR ? original : "",
                extractAvoidTerms(original));
        MusicIntentHints hints = new MusicIntentHints(
                cleanTerms(plan.genres(), 5), cleanTerms(plan.moods(), 5), cleanTerms(plan.scenes(), 5),
                inferLanguages(original));

        MusicSearchTask primary = primaryTask(original, intent, hard);
        List<MusicSearchTask> expansions = expansionTasks(original, intent, primary, plan.tasks());
        List<MusicToolCall> calls = new ArrayList<>();
        calls.add(new MusicToolCall("qq_direct", MusicToolName.QQ_DIRECT_SEARCH,
                List.of(primary), List.of()));
        if (!expansions.isEmpty()) {
            calls.add(new MusicToolCall("qq_expand", MusicToolName.QQ_EXPANDED_SEARCH,
                    expansions, List.of("qq_direct")));
        }
        List<MusicSearchTask> openTasks = new ArrayList<>();
        openTasks.add(primary);
        openTasks.addAll(expansions);
        String openDependency = expansions.isEmpty() ? "qq_direct" : "qq_expand";
        calls.add(new MusicToolCall("open_catalog", MusicToolName.OPEN_CATALOG_SEARCH,
                openTasks.stream().limit(3).toList(), List.of(openDependency)));
        calls.add(new MusicToolCall("video_fallback", MusicToolName.VIDEO_FALLBACK_SEARCH,
                List.of(primary), List.of("open_catalog")));

        return new MusicExecutionPlan(original, intent, hard, soft, hints, calls,
                plan.confidence(), plan.clarificationQuestion());
    }

    public MusicExecutionPlan withEntityCorrection(MusicExecutionPlan plan, String canonicalName,
                                                   MusicEntityType entityType) {
        String canonical = clean(canonicalName, 120);
        if (!StringUtils.hasText(canonical) || entityType == null || entityType == MusicEntityType.UNKNOWN) {
            return plan;
        }
        MusicSearchIntent intent = switch (entityType) {
            case TRACK -> MusicSearchIntent.EXACT_TRACK;
            case ARTIST -> MusicSearchIntent.ARTIST;
            case ALBUM -> MusicSearchIntent.ALBUM;
            default -> MusicSearchIntent.ENTITY_RELATED;
        };
        MusicHardConstraints hard = switch (entityType) {
            case TRACK -> new MusicHardConstraints(canonical, List.of(), null);
            case ARTIST -> new MusicHardConstraints(null, List.of(canonical), null);
            case ALBUM -> new MusicHardConstraints(null, List.of(), canonical);
            default -> new MusicHardConstraints(null, List.of(), null);
        };
        MusicSearchTaskType taskType = switch (intent) {
            case EXACT_TRACK -> MusicSearchTaskType.TRACK;
            case ARTIST -> MusicSearchTaskType.ARTIST;
            case ALBUM -> MusicSearchTaskType.ALBUM;
            default -> MusicSearchTaskType.ENTITY;
        };
        MusicSearchTask task = new MusicSearchTask(taskType, canonical,
                hard.track(), hard.artists().isEmpty() ? null : hard.artists().get(0), hard.album());
        List<MusicToolCall> calls = List.of(
                new MusicToolCall("qq_direct", MusicToolName.QQ_DIRECT_SEARCH, List.of(task), List.of()),
                new MusicToolCall("open_catalog", MusicToolName.OPEN_CATALOG_SEARCH,
                        List.of(task), List.of("qq_direct")),
                new MusicToolCall("video_fallback", MusicToolName.VIDEO_FALLBACK_SEARCH,
                        List.of(task), List.of("open_catalog")));
        return new MusicExecutionPlan(plan.description(), intent, hard,
                new MusicSoftIntent("", plan.softIntent().avoid()), plan.hints(), calls,
                1.0, null);
    }

    public MusicUnderstandingBo understanding(MusicExecutionPlan plan, List<String> rejectedTrackIds) {
        String canonical;
        MusicEntityType type;
        switch (plan.intent()) {
            case EXACT_TRACK -> {
                canonical = plan.hardConstraints().track();
                type = MusicEntityType.TRACK;
            }
            case ARTIST -> {
                canonical = first(plan.hardConstraints().artists());
                type = MusicEntityType.ARTIST;
            }
            case ALBUM -> {
                canonical = plan.hardConstraints().album();
                type = MusicEntityType.ALBUM;
            }
            case ENTITY_RELATED -> {
                canonical = plan.tool(MusicToolName.QQ_DIRECT_SEARCH)
                        .flatMap(call -> call.tasks().stream().findFirst())
                        .map(MusicSearchTask::query).orElse(plan.description());
                type = inferEntityType(plan.description());
            }
            default -> {
                return MusicUnderstandingBo.unresolved();
            }
        }
        if (!StringUtils.hasText(canonical)) {
            return MusicUnderstandingBo.unresolved();
        }
        return new MusicUnderstandingBo(-1L, canonical, type, List.of(canonical), plan.confidence(),
                List.of("music_planner"), List.of(), List.of(),
                rejectedTrackIds == null ? List.of() : List.copyOf(rejectedTrackIds));
    }

    private static MusicSearchTask primaryTask(String original, MusicSearchIntent intent,
                                               MusicHardConstraints hard) {
        String artist = first(hard.artists());
        return switch (intent) {
            case EXACT_TRACK -> new MusicSearchTask(
                    StringUtils.hasText(artist) ? MusicSearchTaskType.TRACK_ARTIST : MusicSearchTaskType.TRACK,
                    firstText(join(hard.track(), artist), MusicTextNormalizer.primarySearchQuery(original)),
                    hard.track(), artist, null);
            case ARTIST -> new MusicSearchTask(MusicSearchTaskType.ARTIST,
                    firstText(artist, MusicTextNormalizer.primarySearchQuery(original)), null, artist, null);
            case ALBUM -> new MusicSearchTask(MusicSearchTaskType.ALBUM,
                    firstText(join(hard.album(), artist), MusicTextNormalizer.primarySearchQuery(original)),
                    null, artist, hard.album());
            case ENTITY_RELATED -> new MusicSearchTask(MusicSearchTaskType.ENTITY,
                    MusicTextNormalizer.primarySearchQuery(original), null, null, null);
            case DISCOVERY -> new MusicSearchTask(MusicSearchTaskType.SCENE,
                    MusicTextNormalizer.primarySearchQuery(original), null, null, null);
            case SIMILAR -> new MusicSearchTask(MusicSearchTaskType.SIMILAR,
                    MusicTextNormalizer.primarySearchQuery(original), hard.track(), artist, hard.album());
            case AMBIGUOUS -> new MusicSearchTask(MusicSearchTaskType.KEYWORDS,
                    MusicTextNormalizer.primarySearchQuery(original), null, null, null);
        };
    }

    private static List<MusicSearchTask> expansionTasks(String original, MusicSearchIntent intent,
                                                        MusicSearchTask primary,
                                                        List<MusicSearchTask> proposed) {
        if (proposed == null || proposed.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, MusicSearchTask> unique = new LinkedHashMap<>();
        for (MusicSearchTask task : proposed) {
            MusicSearchTask sanitized = sanitize(task);
            if (sanitized == null || !compatible(intent, sanitized.type())
                    || introducesUnsupportedQualifier(original, sanitized.query())) {
                continue;
            }
            String key = MusicTextNormalizer.normalize(sanitized.query());
            if (!key.equals(MusicTextNormalizer.normalize(primary.query()))) {
                unique.putIfAbsent(key, sanitized);
            }
            if (unique.size() >= MAX_EXPANSIONS) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private static MusicSearchTask sanitize(MusicSearchTask task) {
        if (task == null || task.type() == null) return null;
        String query = clean(task.query(), 100);
        if (!StringUtils.hasText(query)) return null;
        return new MusicSearchTask(task.type(), query, clean(task.track(), 80),
                clean(task.artist(), 80), clean(task.album(), 80));
    }

    private static boolean compatible(MusicSearchIntent intent, MusicSearchTaskType type) {
        return switch (intent) {
            case EXACT_TRACK -> type == MusicSearchTaskType.TRACK || type == MusicSearchTaskType.TRACK_ARTIST;
            case ARTIST -> type == MusicSearchTaskType.ARTIST;
            case ALBUM -> type == MusicSearchTaskType.ALBUM;
            case ENTITY_RELATED -> type == MusicSearchTaskType.ENTITY || type == MusicSearchTaskType.KEYWORDS;
            case DISCOVERY -> Set.of(MusicSearchTaskType.GENRE, MusicSearchTaskType.MOOD,
                    MusicSearchTaskType.SCENE, MusicSearchTaskType.KEYWORDS).contains(type);
            case SIMILAR -> type == MusicSearchTaskType.SIMILAR || type == MusicSearchTaskType.KEYWORDS;
            case AMBIGUOUS -> type == MusicSearchTaskType.KEYWORDS;
        };
    }

    private static boolean introducesUnsupportedQualifier(String original, String query) {
        String source = original.toLowerCase(Locale.ROOT);
        String candidate = query.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> entry : GUARDED_EXPANSIONS.entrySet()) {
            boolean candidateContains = entry.getValue().stream().anyMatch(candidate::contains);
            boolean sourceContains = entry.getValue().stream().anyMatch(source::contains);
            if (candidateContains && !sourceContains) {
                return true;
            }
        }
        return false;
    }

    private static List<String> extractAvoidTerms(String description) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Matcher matcher = AVOID_PATTERN.matcher(description == null ? "" : description);
        while (matcher.find() && result.size() < 6) {
            String value = clean(matcher.group(1), 40);
            if (StringUtils.hasText(value)) result.add(value);
        }
        return List.copyOf(result);
    }

    private static MusicEntityType inferEntityType(String description) {
        String value = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (value.matches(".*(?:冠军赛|赛事|联赛|比赛|event|champion).*")) return MusicEntityType.EVENT;
        if (value.matches(".*(?:动漫|动画|番剧|anime).*")) return MusicEntityType.ANIME;
        if (value.matches(".*(?:电影|影视|电视剧|film|movie).*")) return MusicEntityType.FILM;
        if (value.matches(".*(?:游戏|game).*")) return MusicEntityType.GAME;
        if (value.matches(".*(?:原声|原声带|soundtrack|ost).*")) return MusicEntityType.SOUNDTRACK;
        return MusicEntityType.FRANCHISE;
    }

    private static List<String> inferLanguages(String description) {
        String value = description == null ? "" : description.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> languages = new LinkedHashSet<>();
        if (value.matches(".*(?:中文|华语|国语|普通话|mandarin|chinese).*")) languages.add("Chinese");
        if (value.matches(".*(?:粤语|广东话|cantonese).*")) languages.add("Cantonese");
        if (value.matches(".*(?:英语|英文|english).*")) languages.add("English");
        if (value.matches(".*(?:日语|日文|japanese).*")) languages.add("Japanese");
        if (value.matches(".*(?:韩语|韩文|korean).*")) languages.add("Korean");
        if (value.matches(".*(?:法语|法文|french).*")) languages.add("French");
        if (value.matches(".*(?:西班牙语|西语|spanish).*")) languages.add("Spanish");
        if (value.matches(".*(?:纯音乐|无人声|instrumental).*")) languages.add("Instrumental");
        return List.copyOf(languages);
    }

    private static List<String> cleanTerms(List<String> values, int max) {
        if (values == null) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = clean(value, 40);
            if (StringUtils.hasText(cleaned)) result.add(cleaned);
            if (result.size() >= max) break;
        }
        return List.copyOf(result);
    }

    private static String clean(String value, int max) {
        String cleaned = MusicTextNormalizer.cleanRequest(value);
        if (!StringUtils.hasText(cleaned)) return null;
        return cleaned.length() > max ? cleaned.substring(0, max).strip() : cleaned;
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String join(String... values) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String value : values) if (StringUtils.hasText(value)) parts.add(value.strip());
        return String.join(" ", parts);
    }
}
