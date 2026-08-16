package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MusicTaxonomyService {
    private static final int MAX_TERMS = 5;
    private static final int MAX_TASKS = 5;
    private static final Pattern TITLE_MARKS = Pattern.compile("[《\\\"“]([^》\\\"”]{1,80})[》\\\"”]");
    private static final Pattern BY_PATTERN = Pattern.compile("(?i)^(.{1,80}?)\\s+by\\s+(.{1,80})$");
    private static final Pattern ARTIST_SONG_PATTERN = Pattern.compile("^(.{1,60}?)(?:的歌|的歌曲|的音乐)$");
    private static final Pattern ARTIST_TRACK_PATTERN = Pattern.compile("^(.{1,60}?)的(?:歌曲|单曲)?[《\\\"“]?([^》\\\"”]{1,80})[》\\\"”]?$" );

    private static final Map<String, String> GENRES = orderedMap(
            "氛围电子", "ambient electronic", "未来贝斯", "future bass", "深浩室", "deep house",
            "电子乐", "electronic", "电子", "electronic", "摇滚", "rock", "流行", "pop",
            "爵士", "jazz", "古典", "classical", "民谣", "folk", "嘻哈", "hip hop",
            "说唱", "hip hop", "氛围", "ambient", "朋克", "punk", "金属", "metal",
            "蓝调", "blues", "雷鬼", "reggae", "乡村", "country", "放克", "funk",
            "轻音乐", "easy listening", "纯音乐", "instrumental", "后摇", "post rock",
            "蒸汽波", "vaporwave", "合成器浪潮", "synthwave", "低保真", "lo-fi",
            "浩室", "house", "科技舞曲", "techno", "陷阱", "trap", "鼓打贝斯", "drum and bass",
            "节奏布鲁斯", "r&b", "灵魂乐", "soul", "世界音乐", "world music");

    private static final Map<String, String> MOODS = orderedMap(
            "安静", "calm", "平静", "peaceful", "放松", "relaxing", "治愈", "soothing",
            "未来感", "futuristic", "伤感", "melancholy", "悲伤", "sad", "开心", "uplifting",
            "快乐", "upbeat", "浪漫", "romantic", "温柔", "tender", "热血", "energizing",
            "激昂", "epic", "神秘", "mysterious", "黑暗", "dark", "梦幻", "dreamy",
            "专注", "focused", "慵懒", "easygoing", "孤独", "lonely");

    private static final Map<String, String> SCENES = orderedMap(
            "写代码", "coding", "编程", "coding", "深夜", "late night", "学习", "study",
            "工作", "work", "运动", "workout", "健身", "workout", "跑步", "running",
            "睡眠", "sleep", "助眠", "sleep", "开车", "driving", "驾车", "driving",
            "通勤", "commute", "派对", "party", "咖啡馆", "coffee shop", "阅读", "reading",
            "冥想", "meditation", "旅行", "travel", "游戏", "gaming");

    public MusicSearchPlan fallbackPlan(String description) {
        String input = clean(description, 160);
        List<String> genres = extract(input, GENRES);
        List<String> moods = extract(input, MOODS);
        List<String> scenes = extract(input, SCENES);
        MusicSearchIntent intent = detectIntent(input, genres, moods, scenes);
        EntityGuess entities = guessEntities(input, intent);
        double confidence = intent == MusicSearchIntent.AMBIGUOUS ? 0.45 : 0.72;
        String question = intent == MusicSearchIntent.AMBIGUOUS
                ? "“" + input + "”是歌曲名、歌手名还是专辑名？" : null;
        MusicSearchPlan base = new MusicSearchPlan(intent, entities.track(), entities.artists(), entities.album(),
                genres, moods, scenes, List.of(), confidence, question);
        return withTasks(base, List.of(), input);
    }

    public MusicSearchPlan enrich(MusicSearchPlan proposed, String description) {
        MusicSearchPlan fallback = fallbackPlan(description);
        if (proposed == null) {
            return fallback;
        }
        MusicSearchIntent intent = proposed.intent() == null ? fallback.intent() : proposed.intent();
        String track = firstText(proposed.track(), fallback.track());
        String album = firstText(proposed.album(), fallback.album());
        List<String> artists = preferTerms(proposed.artists(), fallback.artists(), Map.of());
        List<String> genres = mergeTerms(proposed.genres(), fallback.genres(), GENRES);
        List<String> moods = mergeTerms(proposed.moods(), fallback.moods(), MOODS);
        List<String> scenes = mergeTerms(proposed.scenes(), fallback.scenes(), SCENES);
        double confidence = proposed.confidence() > 0
                ? Math.max(0, Math.min(1, proposed.confidence())) : fallback.confidence();
        String clarification = clean(proposed.clarificationQuestion(), 120);
        if (intent == MusicSearchIntent.AMBIGUOUS && !StringUtils.hasText(clarification)) {
            clarification = fallback.clarificationQuestion();
        }
        MusicSearchPlan base = new MusicSearchPlan(intent, track, artists, album, genres, moods, scenes,
                List.of(), confidence, clarification);
        return withTasks(base, proposed.tasks(), clean(description, 160));
    }

    private MusicSearchPlan withTasks(MusicSearchPlan plan, List<MusicSearchTask> proposedTasks, String original) {
        LinkedHashMap<String, MusicSearchTask> tasks = new LinkedHashMap<>();
        boolean discovery = plan.intent() == MusicSearchIntent.DISCOVERY || plan.intent() == MusicSearchIntent.SIMILAR;
        if (!discovery) {
            generatedTasks(plan, original).forEach(task -> putTask(tasks, task));
        }
        if (proposedTasks != null) {
            proposedTasks.stream().map(this::sanitizeTask)
                    .filter(task -> compatible(plan.intent(), task))
                    .forEach(task -> putTask(tasks, task));
        }
        if (discovery || tasks.isEmpty()) {
            generatedTasks(plan, original).forEach(task -> putTask(tasks, task));
        }
        List<MusicSearchTask> result = tasks.values().stream().limit(MAX_TASKS).toList();
        return new MusicSearchPlan(plan.intent(), plan.track(), plan.artists(), plan.album(), plan.genres(),
                plan.moods(), plan.scenes(), result, plan.confidence(), plan.clarificationQuestion());
    }

    private List<MusicSearchTask> generatedTasks(MusicSearchPlan plan, String original) {
        String artist = plan.artists().isEmpty() ? null : plan.artists().get(0);
        List<MusicSearchTask> tasks = new ArrayList<>();
        switch (plan.intent()) {
            case EXACT_TRACK -> {
                if (StringUtils.hasText(plan.track()) && StringUtils.hasText(artist)) {
                    tasks.add(task(MusicSearchTaskType.TRACK_ARTIST,
                            join(plan.track(), artist), plan.track(), artist, null));
                }
                if (StringUtils.hasText(plan.track())) {
                    tasks.add(task(MusicSearchTaskType.TRACK, plan.track(), plan.track(), null, null));
                }
            }
            case ARTIST -> tasks.add(task(MusicSearchTaskType.ARTIST,
                    firstText(artist, original), null, firstText(artist, original), null));
            case ALBUM -> tasks.add(task(MusicSearchTaskType.ALBUM,
                    join(plan.album(), artist), null, artist, plan.album()));
            case ENTITY_RELATED -> tasks.add(task(MusicSearchTaskType.ENTITY,
                    original, null, null, null));
            case SIMILAR -> tasks.add(task(MusicSearchTaskType.SIMILAR,
                    join(artist, plan.track(), first(plan.genres()), "similar music"),
                    plan.track(), artist, plan.album()));
            case DISCOVERY -> addDiscoveryTasks(tasks, plan, original);
            case AMBIGUOUS -> tasks.add(task(MusicSearchTaskType.KEYWORDS, original, null, null, null));
        }
        if (tasks.isEmpty() && StringUtils.hasText(original)) {
            tasks.add(task(MusicSearchTaskType.KEYWORDS, original, null, null, null));
        }
        return tasks;
    }

    private void addDiscoveryTasks(List<MusicSearchTask> tasks, MusicSearchPlan plan, String original) {
        String genre = first(plan.genres());
        String mood = first(plan.moods());
        String scene = first(plan.scenes());
        addIfText(tasks, MusicSearchTaskType.GENRE, join(genre, mood), null, null, null);
        addIfText(tasks, MusicSearchTaskType.MOOD, join(mood, genre, "music"), null, null, null);
        addIfText(tasks, MusicSearchTaskType.SCENE, join(scene, genre, mood), null, null, null);
        if (plan.genres().size() > 1) {
            addIfText(tasks, MusicSearchTaskType.GENRE,
                    join(plan.genres().get(1), mood, scene), null, null, null);
        }
        if (tasks.isEmpty()) {
            addIfText(tasks, MusicSearchTaskType.KEYWORDS, original, null, null, null);
        }
    }

    private void addIfText(List<MusicSearchTask> tasks, MusicSearchTaskType type, String query,
                           String track, String artist, String album) {
        if (StringUtils.hasText(query)) {
            tasks.add(task(type, query, track, artist, album));
        }
    }

    private MusicSearchTask sanitizeTask(MusicSearchTask value) {
        if (value == null) {
            return null;
        }
        return task(value.type() == null ? MusicSearchTaskType.KEYWORDS : value.type(),
                value.query(), value.track(), value.artist(), value.album());
    }

    private static boolean compatible(MusicSearchIntent intent, MusicSearchTask task) {
        if (task == null || task.type() == null) return false;
        return switch (intent) {
            case EXACT_TRACK -> task.type() == MusicSearchTaskType.TRACK
                    || task.type() == MusicSearchTaskType.TRACK_ARTIST;
            case ARTIST -> task.type() == MusicSearchTaskType.ARTIST;
            case ALBUM -> task.type() == MusicSearchTaskType.ALBUM;
            case ENTITY_RELATED -> task.type() == MusicSearchTaskType.ENTITY;
            case SIMILAR -> task.type() == MusicSearchTaskType.SIMILAR
                    || task.type() == MusicSearchTaskType.KEYWORDS;
            case DISCOVERY -> task.type() == MusicSearchTaskType.GENRE
                    || task.type() == MusicSearchTaskType.MOOD
                    || task.type() == MusicSearchTaskType.SCENE
                    || task.type() == MusicSearchTaskType.KEYWORDS;
            case AMBIGUOUS -> task.type() == MusicSearchTaskType.KEYWORDS;
        };
    }

    private static MusicSearchTask task(MusicSearchTaskType type, String query,
                                        String track, String artist, String album) {
        return new MusicSearchTask(type, clean(query, 100), clean(track, 80),
                clean(artist, 80), clean(album, 80));
    }

    private static void putTask(LinkedHashMap<String, MusicSearchTask> target, MusicSearchTask task) {
        if (task == null || !StringUtils.hasText(task.query()) || target.size() >= MAX_TASKS) {
            return;
        }
        target.putIfAbsent(normalize(task.type() + "|" + task.query()), task);
    }

    private static MusicSearchIntent detectIntent(String input, List<String> genres,
                                                   List<String> moods, List<String> scenes) {
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.matches(".*(?:类似|相似|像.+一样|similar to|sounds like).*")) {
            return MusicSearchIntent.SIMILAR;
        }
        if (lower.matches(".*(?:专辑|album|\\bep\\b).*")) {
            return MusicSearchIntent.ALBUM;
        }
        if (ARTIST_SONG_PATTERN.matcher(stripLeadingAction(input)).matches()
                || lower.matches(".*(?:歌手|艺人|乐队|组合|artist)[:： ]*[^ ]+.*")) {
            return MusicSearchIntent.ARTIST;
        }
        if (TITLE_MARKS.matcher(input).find()
                || lower.matches("^(?:播放|听|找|搜索|来一首|来首|play|find|search)\\s+.+")
                || lower.contains("歌曲") || lower.contains("单曲")) {
            return MusicSearchIntent.EXACT_TRACK;
        }
        if (!genres.isEmpty() || !moods.isEmpty() || !scenes.isEmpty()
                || lower.matches(".*(?:适合|推荐|想听|来点|风格|类型|氛围|genre|mood|music for).*")) {
            return MusicSearchIntent.DISCOVERY;
        }
        return MusicSearchIntent.AMBIGUOUS;
    }

    private static EntityGuess guessEntities(String input, MusicSearchIntent intent) {
        String stripped = stripLeadingAction(input);
        Matcher by = BY_PATTERN.matcher(stripped);
        if (by.matches()) {
            return new EntityGuess(clean(by.group(1), 80), List.of(clean(by.group(2), 80)), null);
        }
        Matcher title = TITLE_MARKS.matcher(input);
        String markedTitle = title.find() ? clean(title.group(1), 80) : null;
        Matcher artistSongs = ARTIST_SONG_PATTERN.matcher(stripped);
        if (intent == MusicSearchIntent.ARTIST && artistSongs.matches()) {
            return new EntityGuess(null, List.of(clean(artistSongs.group(1), 80)), null);
        }
        Matcher artistTrack = ARTIST_TRACK_PATTERN.matcher(stripped);
        if (intent == MusicSearchIntent.EXACT_TRACK && artistTrack.matches()
                && !artistTrack.group(2).matches("歌|歌曲|音乐")) {
            return new EntityGuess(clean(artistTrack.group(2), 80),
                    List.of(clean(artistTrack.group(1), 80)), null);
        }
        if (intent == MusicSearchIntent.ALBUM) {
            String album = markedTitle;
            if (!StringUtils.hasText(album)) {
                album = clean(stripped.replaceFirst("(?i)^.*?(?:专辑|album|\\bep\\b)[:： ]*", ""), 80);
            }
            String artist = stripped.contains("的专辑") ? clean(stripped.substring(0, stripped.indexOf("的专辑")), 80) : null;
            return new EntityGuess(null, StringUtils.hasText(artist) ? List.of(artist) : List.of(), album);
        }
        if (intent == MusicSearchIntent.ARTIST) {
            String artist = clean(stripped.replaceFirst("(?i)^(?:歌手|艺人|乐队|组合|artist)[:： ]*", ""), 80);
            return new EntityGuess(null, StringUtils.hasText(artist) ? List.of(artist) : List.of(), null);
        }
        if (intent == MusicSearchIntent.EXACT_TRACK) {
            String track = StringUtils.hasText(markedTitle) ? markedTitle
                    : clean(stripped.replaceFirst("^(?:歌曲|歌|单曲)[:： ]*", ""), 80);
            return new EntityGuess(track, List.of(), null);
        }
        return new EntityGuess(markedTitle, List.of(), null);
    }

    private static String stripLeadingAction(String value) {
        return clean(value, 160).replaceFirst(
                "(?i)^(?:请|帮我|我想|想要|给我)?\\s*(?:播放|听听|听|找找|找|搜索|来一首|来首|推荐|play|find|search)\\s*", "").strip();
    }

    private static List<String> extract(String input, Map<String, String> vocabulary) {
        String lower = input.toLowerCase(Locale.ROOT);
        Set<String> result = new LinkedHashSet<>();
        vocabulary.forEach((source, target) -> {
            if (lower.contains(source.toLowerCase(Locale.ROOT))) {
                result.add(target);
            }
        });
        return result.stream().limit(MAX_TERMS).toList();
    }

    private static List<String> preferTerms(List<String> primary, List<String> fallback, Map<String, String> vocabulary) {
        List<String> sanitized = sanitizeTerms(primary, vocabulary);
        return sanitized.isEmpty() ? sanitizeTerms(fallback, vocabulary) : sanitized;
    }

    private static List<String> mergeTerms(List<String> primary, List<String> fallback, Map<String, String> vocabulary) {
        LinkedHashSet<String> result = new LinkedHashSet<>(sanitizeTerms(primary, vocabulary));
        result.addAll(sanitizeTerms(fallback, vocabulary));
        return result.stream().limit(MAX_TERMS).toList();
    }

    private static List<String> sanitizeTerms(List<String> values, Map<String, String> vocabulary) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = clean(value, 40);
            if (StringUtils.hasText(cleaned)) {
                result.add(vocabulary.getOrDefault(cleaned.toLowerCase(Locale.ROOT), cleaned));
            }
        }
        return result.stream().limit(MAX_TERMS).toList();
    }

    private static String join(String... values) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value.strip());
            }
        }
        return String.join(" ", parts);
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String firstText(String primary, String fallback) {
        String cleaned = clean(primary, 80);
        return StringUtils.hasText(cleaned) ? cleaned : clean(fallback, 80);
    }

    private static String clean(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = value.strip().replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ");
        if ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("“") && result.endsWith("”"))) {
            result = result.substring(1, result.length() - 1).strip();
        }
        return result.length() > maxLength ? result.substring(0, maxLength).strip() : result;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static Map<String, String> orderedMap(String... entries) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(entries[i], entries[i + 1]);
        }
        return Collections.unmodifiableMap(result);
    }

    private record EntityGuess(String track, List<String> artists, String album) {
    }
}
