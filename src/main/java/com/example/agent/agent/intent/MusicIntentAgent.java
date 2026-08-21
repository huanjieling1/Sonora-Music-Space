package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Hybrid semantic router. A zero-temperature language agent proposes slots while Java rules preserve literal
 * targets, strict commands, capability boundaries and deterministic fallback behavior.
 */
@Component
public class MusicIntentAgent {
    private static final Pattern PLAYLIST_SUBJECT = Pattern.compile("歌单|播放列表|playlist", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANDOM_PLAYLIST_ACTION = Pattern.compile(
            "随机|随便|任意|抽(?:取|一个|一份)?|random|shuffle", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYLIST_CREATION_ACTION = Pattern.compile(
            "创建|新建|生成|制作|定制|做(?:一个|一份)|create|generate", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYLIST_SEARCH_ACTION = Pattern.compile(
            "搜索|搜一下|查找|找|推荐|给我|来点|来些|来一些|来个|来份|适合|想听|相关|"
                    + "search|find|recommend|some", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMPLICIT_RELATIONAL_QUERY = Pattern.compile(
            ".+(?:的|相关的?|有关的?)(?:歌单|播放列表|歌曲|歌|音乐|专辑|作品)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_SUBJECT = Pattern.compile(
            "歌手|艺人|乐队|组合|音乐人|创作人|composer|singer|artist|band|group", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_LOOKUP_ACTION = Pattern.compile(
            "搜索|搜一下|查找|找|介绍|了解|资料|档案|是谁|生涯|作品|search|find|introduce|profile|about",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MUSIC_SUBJECT = Pattern.compile(
            "歌|歌曲|音乐|曲目|歌手|专辑|歌单|主题曲|原声|配乐|ost|soundtrack|song|music|track|artist|album|playlist",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DISCOVERY_ACTION = Pattern.compile(
            "搜索|搜一下|查找|找|推荐|给我|来点|来首|想听|听一下|听点|播放|放一首|放点|search|find|recommend|listen|play",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAYBACK_ACTION = Pattern.compile(
            "想听|给我听|听一下|听点|开始听|播放|放一首|放点|listen|play", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESULT_PLAYBACK_ACTION = Pattern.compile(
            "(?:播放|放|听)?第[一二三四五六七八九十\\d]+首|播放这首|放这首", Pattern.CASE_INSENSITIVE);
    private static final Pattern RESULT_PAGE_ACTION = Pattern.compile(
            "第[一二三四五六七八九十\\d]+页|上一页|下一页", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUEUE_ACTION = Pattern.compile(
            "加入队列|添加到队列|全部播放|全部加入|queue", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE_SUBJECT = Pattern.compile(
            "音乐画像|用户画像|我的画像|我的偏好|音乐偏好|收听偏好|我喜欢什么|对我了解多少|profile|my taste",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE_READ_ACTION = Pattern.compile(
            "分析|总结|查看|看看|展示|告诉我|是什么|有哪些|了解|解释|describe|analy[sz]e|summari[sz]e|show",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PERSONALIZED_DISCOVERY = Pattern.compile(
            "推荐|适合(?:我|我的|当前|现在|学习|工作|通勤|跑步|睡前|夜晚)?|根据我的|按照我的|按我的|"
                    + "猜你喜欢|懂我|符合我(?:的)?口味|我的口味|我的品味|来点|来些|来一些|随便来|发现音乐|相似音乐|"
                    + "recommend|for me|my taste|surprise me|similar",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FAVORITE_ARTIST_INFERENCE = Pattern.compile(
            "我最喜欢的(?:歌手|艺人|乐队|组合)|我最爱的(?:歌手|艺人|乐队|组合)|"
                    + "我最常听的(?:歌手|艺人|乐队|组合)|你认为我.*(?:喜欢|偏爱|常听).*(?:歌手|艺人|乐队|组合)|"
                    + "根据.*(?:画像|记录|了解).*(?:喜欢|偏爱|常听).*(?:歌手|艺人|乐队|组合)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARTIST_PROFILE_RESULT = Pattern.compile(
            "资料|档案|介绍|信息|详情|生涯|成就|曲风|风格|找出来|查出来|profile|about",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRENDING = Pattern.compile(
            "热度|热门|最火|火的|排行榜|排行|榜单|飙升|上升最快|流行指数|热歌|"
                    + "trending|chart|hottest|most popular", Pattern.CASE_INSENSITIVE);
    private static final Pattern RISING = Pattern.compile(
            "飙升|上升最快|涨得最快|rising|fastest rising", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEWNESS = Pattern.compile(
            "新歌|最新发行|刚发行|new releases?|new songs?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CORRECTION = Pattern.compile(
            "我是说|我说的是|不是.*是|改成|换成|纠正|i mean|rather than", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOW_UP_FEEDBACK = Pattern.compile(
            "这些|这批|刚才|上批|换一批|重新推荐|再推荐|更喜欢|偏爱|不喜欢|不想听|不合口味|不对胃口",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SOCIAL = Pattern.compile(
            "^(?:你好|您好|嗨|hi|hello|谢谢|感谢|好的|好|明白了|再见|晚安|早上好|下午好)[！!。,.， ]*$",
            Pattern.CASE_INSENSITIVE);

    private final MusicSemanticIntentInterpreter interpreter;
    private final MusicIntentContextStore contextStore;

    public MusicIntentAgent() {
        this(null, null);
    }

    @Autowired
    public MusicIntentAgent(MusicSemanticIntentInterpreter interpreter, MusicIntentContextStore contextStore) {
        this.interpreter = interpreter;
        this.contextStore = contextStore;
    }

    /** Semantic contract first; literal nouns and safety-critical commands remain Java-enforced. */
    public MusicIntentUnderstanding analyze(MusicAgentTurn turn) {
        if (turn == null) return MusicIntentUnderstanding.routed(MusicAgentRoute.CONVERSATION,
                MusicIntentDraft.unknown());
        String request = turn.request();
        MusicIntentDraft fallback = deterministicDraft(request);
        MusicIntentDraft candidate = fallback;
        if (!strictCommand(request) && !FOLLOW_UP_FEEDBACK.matcher(request).find() && interpreter != null) {
            candidate = interpreter.understand(request).map(value -> merge(request, fallback, value))
                    .orElse(fallback);
        }
        if (CORRECTION.matcher(request).find() && contextStore != null) {
            MusicIntentDraft current = candidate;
            candidate = contextStore.latest(turn.memoryId()).map(previous -> mergeCorrection(previous, current))
                    .orElse(candidate);
        }
        MusicIntentUnderstanding result = decide(request, candidate);
        if (contextStore != null && candidate.action() != MusicIntentDraft.Action.CONVERSATION) {
            contextStore.put(turn.memoryId(), candidate);
        }
        return result;
    }

    public MusicAgentRoute classify(String message) {
        return decide(message, deterministicDraft(message)).route();
    }

    private static MusicIntentUnderstanding decide(String request, MusicIntentDraft intent) {
        if (intent.domain() == MusicIntentDraft.Domain.OTHER || intent.domain() == MusicIntentDraft.Domain.SOCIAL) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.CONVERSATION, intent);
        }
        if (FOLLOW_UP_FEEDBACK.matcher(safe(request)).find()) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.CONVERSATION,
                    with(intent, MusicIntentDraft.Mode.FOLLOW_UP));
        }
        if (intent.action() == MusicIntentDraft.Action.CAPABILITY_INQUIRY) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.CAPABILITY_INQUIRY, intent);
        }
        if (shouldResolveFavoriteArtistProfile(request)) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE, intent);
        }
        if (intent.mode() == MusicIntentDraft.Mode.TRENDING
                || intent.rankingMetric() != MusicIntentDraft.RankingMetric.NONE) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.QQ_TREND_DISCOVERY, intent);
        }
        if (intent.target() == MusicIntentDraft.Target.PLAYLIST) {
            if (intent.action() == MusicIntentDraft.Action.UNKNOWN) {
                return MusicIntentUnderstanding.clarify(intent,
                        "你想按自己的口味推荐歌单，还是查找某个主题、歌手或场景的 QQ 音乐歌单？");
            }
            return MusicIntentUnderstanding.routed(intent.mode() == MusicIntentDraft.Mode.RANDOM
                    ? MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST : MusicAgentRoute.PLAYLIST_SEARCH, intent);
        }
        if (intent.target() == MusicIntentDraft.Target.ARTIST
                && intent.action() != MusicIntentDraft.Action.UNKNOWN) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.ARTIST_LOOKUP, intent);
        }
        if (intent.target() == MusicIntentDraft.Target.PROFILE
                || intent.action() == MusicIntentDraft.Action.ANALYZE_PROFILE) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.PROFILE_ANALYSIS, intent);
        }
        if (intent.action() == MusicIntentDraft.Action.NAVIGATE) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.RESULT_NAVIGATION, intent);
        }
        if (intent.action() == MusicIntentDraft.Action.QUEUE) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.QUEUE_CONTROL, intent);
        }
        if (intent.target() == MusicIntentDraft.Target.SEARCH_RESULT
                && intent.action() == MusicIntentDraft.Action.PLAY) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.RESULT_PLAYBACK, intent);
        }
        if (intent.action() == MusicIntentDraft.Action.RECOMMEND
                || intent.action() == MusicIntentDraft.Action.SEARCH
                || intent.action() == MusicIntentDraft.Action.PLAY) {
            return MusicIntentUnderstanding.routed(MusicAgentRoute.MUSIC_DISCOVERY, intent);
        }
        return MusicIntentUnderstanding.routed(MusicAgentRoute.CONVERSATION, intent);
    }

    private static MusicIntentDraft deterministicDraft(String message) {
        String value = safe(message);
        if (value.isEmpty()) return MusicIntentDraft.unknown();
        MusicIntentDraft.Action action = action(value);
        MusicIntentDraft.Target target = target(value);
        MusicIntentDraft.Mode mode = mode(value, action);
        MusicIntentDraft.RankingMetric ranking = RISING.matcher(value).find()
                ? MusicIntentDraft.RankingMetric.RISING
                : NEWNESS.matcher(value).find() ? MusicIntentDraft.RankingMetric.NEWNESS
                : TRENDING.matcher(value).find() ? MusicIntentDraft.RankingMetric.HOTNESS
                : MusicIntentDraft.RankingMetric.NONE;
        List<String> scenes = scenes(value);
        List<String> missing = target == MusicIntentDraft.Target.PLAYLIST
                && action == MusicIntentDraft.Action.UNKNOWN ? List.of("action_or_direction") : List.of();
        return new MusicIntentDraft(action, target, mode, ranking, timeWindow(value), scenes,
                shouldUseRecommendationProfile(value), missing, confidence(action, target), domain(value, action, target));
    }

    private static MusicIntentDraft.Action action(String value) {
        if (value.matches("(?i).*(?:有哪些能力|有什么能力|能做什么|支持什么|怎么用|如何使用).*$")) {
            return MusicIntentDraft.Action.CAPABILITY_INQUIRY;
        }
        if (shouldAnalyzeProfile(value)) return MusicIntentDraft.Action.ANALYZE_PROFILE;
        if (shouldSearchQqArtists(value)) return MusicIntentDraft.Action.SEARCH;
        if (RESULT_PAGE_ACTION.matcher(value).find()) return MusicIntentDraft.Action.NAVIGATE;
        if (QUEUE_ACTION.matcher(value).find()) return MusicIntentDraft.Action.QUEUE;
        if (RESULT_PLAYBACK_ACTION.matcher(value).find()) return MusicIntentDraft.Action.PLAY;
        if (RANDOM_PLAYLIST_ACTION.matcher(value).find()) return MusicIntentDraft.Action.RECOMMEND;
        if (value.matches("(?is).*(?:推荐|来点|来些|来一些|来个|来份|适合|猜你喜欢|符合.*口味|想听|给我).*$")) {
            return MusicIntentDraft.Action.RECOMMEND;
        }
        if (value.matches("(?is).*(?:搜索|搜一下|查找|找|search|find).*$")) {
            return MusicIntentDraft.Action.SEARCH;
        }
        if (PLAYBACK_ACTION.matcher(value).find()) return MusicIntentDraft.Action.PLAY;
        if (IMPLICIT_RELATIONAL_QUERY.matcher(value).find()) return MusicIntentDraft.Action.SEARCH;
        return MusicIntentDraft.Action.UNKNOWN;
    }

    private static MusicIntentDraft.Target target(String value) {
        if (shouldAnalyzeProfile(value)) return MusicIntentDraft.Target.PROFILE;
        if (PLAYLIST_SUBJECT.matcher(value).find()) return MusicIntentDraft.Target.PLAYLIST;
        if (value.matches("(?is).*(?:排行榜|榜单|排行|top ?list|chart).*$")
                && !value.matches("(?is).*(?:歌手|艺人|乐队).*$")) {
            return MusicIntentDraft.Target.CHART;
        }
        if (RESULT_PAGE_ACTION.matcher(value).find() || RESULT_PLAYBACK_ACTION.matcher(value).find()) {
            return MusicIntentDraft.Target.SEARCH_RESULT;
        }
        if (QUEUE_ACTION.matcher(value).find()) return MusicIntentDraft.Target.QUEUE;
        if (ARTIST_SUBJECT.matcher(value).find() && !value.matches(".*(?:的歌|的歌曲|旗下|作品).*$")) {
            return MusicIntentDraft.Target.ARTIST;
        }
        if (value.matches("(?i).*(?:专辑|album|\\bep\\b).*$")) return MusicIntentDraft.Target.ALBUM;
        if (MUSIC_SUBJECT.matcher(value).find() || PLAYBACK_ACTION.matcher(value).find()) {
            return MusicIntentDraft.Target.TRACK;
        }
        return MusicIntentDraft.Target.NONE;
    }

    private static MusicIntentDraft.Mode mode(String value, MusicIntentDraft.Action action) {
        if (TRENDING.matcher(value).find() || RISING.matcher(value).find() || NEWNESS.matcher(value).find()) {
            return MusicIntentDraft.Mode.TRENDING;
        }
        if (RANDOM_PLAYLIST_ACTION.matcher(value).find()) return MusicIntentDraft.Mode.RANDOM;
        if (CORRECTION.matcher(value).find() || FOLLOW_UP_FEEDBACK.matcher(value).find()) {
            return MusicIntentDraft.Mode.FOLLOW_UP;
        }
        if (action == MusicIntentDraft.Action.RECOMMEND) return MusicIntentDraft.Mode.DISCOVERY;
        if (action == MusicIntentDraft.Action.SEARCH || action == MusicIntentDraft.Action.PLAY) {
            return MusicIntentDraft.Mode.EXACT;
        }
        return MusicIntentDraft.Mode.UNKNOWN;
    }

    private static MusicIntentDraft.TimeWindow timeWindow(String value) {
        if (value.matches("(?is).*(?:实时|现在|当下|此刻|real.?time).*$")) return MusicIntentDraft.TimeWindow.REALTIME;
        if (value.matches("(?is).*(?:一周|本周|这周|近 ?7 ?天|weekly|week).*$")) return MusicIntentDraft.TimeWindow.WEEK;
        if (value.matches("(?is).*(?:一个月|本月|这个月|近 ?30 ?天|monthly|month).*$")) return MusicIntentDraft.TimeWindow.MONTH;
        if (value.matches("(?is).*(?:最近几天|近几天|今日|今天|日榜|daily).*$")) return MusicIntentDraft.TimeWindow.DAY;
        if (value.matches("(?is).*(?:全时间|历史|有史以来|all.?time).*$")) return MusicIntentDraft.TimeWindow.ALL_TIME;
        if (value.matches("(?is).*(?:最近|近期|近来|recent).*$")) return MusicIntentDraft.TimeWindow.RECENT;
        return MusicIntentDraft.TimeWindow.UNSPECIFIED;
    }

    private static List<String> scenes(String value) {
        ArrayList<String> result = new ArrayList<>();
        for (String scene : List.of("深夜", "夜晚", "睡前", "学习", "工作", "通勤", "跑步", "健身", "开车", "旅行", "派对", "阅读")) {
            if (value.contains(scene)) result.add(scene);
        }
        return List.copyOf(result);
    }

    private static MusicIntentDraft.Domain domain(String value, MusicIntentDraft.Action action,
                                                  MusicIntentDraft.Target target) {
        if (SOCIAL.matcher(value).matches()) return MusicIntentDraft.Domain.SOCIAL;
        if (target != MusicIntentDraft.Target.NONE || MUSIC_SUBJECT.matcher(value).find()
                || PLAYBACK_ACTION.matcher(value).find() || action == MusicIntentDraft.Action.CAPABILITY_INQUIRY) {
            return MusicIntentDraft.Domain.MUSIC;
        }
        if (value.matches("(?is)^(?:请|请你|麻烦|帮我|给我|我要|我想|能否|能不能|可以帮我|替我).+")) {
            return MusicIntentDraft.Domain.OTHER;
        }
        return MusicIntentDraft.Domain.UNKNOWN;
    }

    private static MusicIntentDraft merge(String request, MusicIntentDraft fallback, MusicIntentDraft model) {
        MusicIntentDraft.Target literalTarget = target(request);
        MusicIntentDraft.Action literalAction = action(request);
        if (safe(request).matches("(?:歌单|播放列表|playlist)[？?！!。 ]*")) {
            literalAction = MusicIntentDraft.Action.UNKNOWN;
            model = new MusicIntentDraft(MusicIntentDraft.Action.UNKNOWN, model.target(), model.mode(),
                    model.rankingMetric(), model.timeWindow(), model.scenes(), model.personalized(),
                    List.of("action_or_direction"), model.confidence(), model.domain());
        }
        boolean trend = TRENDING.matcher(request).find() || RISING.matcher(request).find() || NEWNESS.matcher(request).find();
        boolean inventedTrend = !trend && (model.mode() == MusicIntentDraft.Mode.TRENDING
                || model.rankingMetric() != MusicIntentDraft.RankingMetric.NONE);
        if (inventedTrend) {
            model = fallback;
        }
        MusicIntentDraft.Mode groundedMode = trend ? MusicIntentDraft.Mode.TRENDING
                : model.mode() == MusicIntentDraft.Mode.TRENDING ? fallback.mode()
                : model.mode() != MusicIntentDraft.Mode.UNKNOWN ? model.mode() : fallback.mode();
        MusicIntentDraft.RankingMetric groundedRanking = trend
                ? fallback.rankingMetric() : MusicIntentDraft.RankingMetric.NONE;
        return new MusicIntentDraft(
                literalAction != MusicIntentDraft.Action.UNKNOWN ? literalAction : model.action(),
                literalTarget != MusicIntentDraft.Target.NONE ? literalTarget : model.target(),
                groundedMode,
                groundedRanking,
                fallback.timeWindow() != MusicIntentDraft.TimeWindow.UNSPECIFIED
                        ? fallback.timeWindow() : model.timeWindow(),
                fallback.scenes().isEmpty() ? model.scenes() : fallback.scenes(),
                fallback.personalized() || model.personalized(), model.missingSlots(), model.confidence(),
                fallback.domain() != MusicIntentDraft.Domain.UNKNOWN ? fallback.domain() : model.domain());
    }

    private static MusicIntentDraft mergeCorrection(MusicIntentDraft previous, MusicIntentDraft current) {
        return new MusicIntentDraft(current.action(),
                current.target() == MusicIntentDraft.Target.NONE ? previous.target() : current.target(),
                current.mode(), current.rankingMetric() == MusicIntentDraft.RankingMetric.NONE
                ? previous.rankingMetric() : current.rankingMetric(),
                current.timeWindow() == MusicIntentDraft.TimeWindow.UNSPECIFIED
                        ? previous.timeWindow() : current.timeWindow(),
                current.scenes().isEmpty() ? previous.scenes() : current.scenes(),
                current.personalized() || previous.personalized(), current.missingSlots(), current.confidence(),
                current.domain() == MusicIntentDraft.Domain.UNKNOWN ? previous.domain() : current.domain());
    }

    private static MusicIntentDraft with(MusicIntentDraft value, MusicIntentDraft.Mode mode) {
        return new MusicIntentDraft(value.action(), value.target(), mode, value.rankingMetric(), value.timeWindow(),
                value.scenes(), value.personalized(), value.missingSlots(), value.confidence(), value.domain());
    }

    private static boolean strictCommand(String request) {
        return RESULT_PAGE_ACTION.matcher(safe(request)).find() || RESULT_PLAYBACK_ACTION.matcher(safe(request)).find()
                || QUEUE_ACTION.matcher(safe(request)).find() || shouldAnalyzeProfile(request)
                || shouldPlayRandomQqPublicPlaylist(request);
    }

    private static double confidence(MusicIntentDraft.Action action, MusicIntentDraft.Target target) {
        if (action == MusicIntentDraft.Action.UNKNOWN && target == MusicIntentDraft.Target.NONE) return 0.2;
        if (action == MusicIntentDraft.Action.UNKNOWN || target == MusicIntentDraft.Target.NONE) return 0.6;
        return 0.9;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean shouldAnalyzeProfile(String message) {
        if (!StringUtils.hasText(message)) return false;
        return PROFILE_SUBJECT.matcher(message).find() && PROFILE_READ_ACTION.matcher(message).find();
    }

    public static boolean shouldPlayRandomQqPublicPlaylist(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        return PLAYLIST_SUBJECT.matcher(normalized).find()
                && RANDOM_PLAYLIST_ACTION.matcher(normalized).find()
                && !PLAYLIST_CREATION_ACTION.matcher(normalized).find();
    }

    public static boolean shouldSearchQqPlaylists(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        return PLAYLIST_SUBJECT.matcher(normalized).find()
                && PLAYLIST_SEARCH_ACTION.matcher(normalized).find()
                && !RANDOM_PLAYLIST_ACTION.matcher(normalized).find()
                && !PLAYLIST_CREATION_ACTION.matcher(normalized).find();
    }

    public static boolean shouldSearchQqArtists(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        return ARTIST_SUBJECT.matcher(normalized).find()
                && ARTIST_LOOKUP_ACTION.matcher(normalized).find()
                && !PLAYLIST_SUBJECT.matcher(normalized).find()
                && !PLAYBACK_ACTION.matcher(normalized).find();
    }

    public static boolean shouldSearch(String message) {
        if (!StringUtils.hasText(message)) return false;
        String normalized = message.strip().toLowerCase(Locale.ROOT);
        if (!DISCOVERY_ACTION.matcher(normalized).find()) return false;
        return MUSIC_SUBJECT.matcher(normalized).find() || PLAYBACK_ACTION.matcher(normalized).find();
    }

    public static boolean wantsPlayback(String message) {
        return StringUtils.hasText(message) && PLAYBACK_ACTION.matcher(message).find();
    }

    public static boolean shouldUseRecommendationProfile(String message) {
        return StringUtils.hasText(message) && (PERSONALIZED_DISCOVERY.matcher(message).find()
                || FAVORITE_ARTIST_INFERENCE.matcher(message).find());
    }

    public static boolean shouldResolveFavoriteArtistProfile(String message) {
        return StringUtils.hasText(message)
                && FAVORITE_ARTIST_INFERENCE.matcher(message).find()
                && ARTIST_PROFILE_RESULT.matcher(message).find();
    }

    public static String failureAnswer(String toolResult) {
        if (!StringUtils.hasText(toolResult)) return "音乐搜索暂时不可用，请稍后再试。";
        String result = toolResult.strip();
        String failedPrefix = "Music catalog request failed: ";
        if (result.startsWith(failedPrefix)) return "音乐搜索失败：" + result.substring(failedPrefix.length());
        if (result.startsWith("Music catalog request failed temporarily")) return "音乐搜索暂时不可用，请稍后再试。";
        return result;
    }
}
