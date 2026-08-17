package com.example.agent.service.impl;

import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import com.example.agent.service.MusicPersonalizationService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns an open-ended recommendation request into profile-grounded search terms.
 * Explicit track, artist, album and named-entity searches remain literal.
 */
@Component
public class MusicRecommendationContextResolver {
    private static final Pattern RECOMMENDATION_LANGUAGE = Pattern.compile(
            "推荐|按(?:照)?我的|根据我的|适合我|猜你喜欢|懂我|随便来|来点|来一些|想听什么|"
                    + "recommend|for me|my taste|surprise me",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_REQUEST_WORDS = Pattern.compile(
            "请|麻烦|帮我|给我|我想|想要|想听|推荐|来点|来一些|随便来点|随便|一些|几个|几首|"
                    + "歌曲|音乐|歌单|曲目|首|个|适合我的|适合|按我的口味|根据我的喜好|猜你喜欢|for me|"
                    + "recommend|some|music|songs?|playlists?",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERIC_TERMS = Set.of(
            "推荐", "歌曲推荐", "音乐推荐", "歌单推荐", "推荐歌曲", "推荐音乐", "推荐歌单",
            "歌单", "歌曲", "音乐", "一些歌", "来点歌", "随便来点", "猜你喜欢",
            "recommend", "recommendmusic", "recommendplaylist", "playlistrecommendation");

    private final MusicPersonalizationService personalizationService;

    public MusicRecommendationContextResolver(MusicPersonalizationService personalizationService) {
        this.personalizationService = personalizationService;
    }

    public RecommendationContext resolve(long userId, String request, MusicSearchIntent intent,
                                         String extractedKeyword) {
        String original = MusicTextNormalizer.cleanRequest(request);
        MusicSearchIntent safeIntent = intent == null ? MusicSearchIntent.AMBIGUOUS : intent;
        boolean recommendation = isRecommendation(original, safeIntent);
        String currentKeyword = meaningfulKeyword(extractedKeyword);
        if (!recommendation) {
            return RecommendationContext.literal(original, currentKeyword);
        }

        MusicProfileVo profile = readProfile(userId);
        List<MusicProfileInsightVo> likes = usable(profile == null || profile.summary() == null
                ? List.of() : profile.summary().likes(), 4);
        List<MusicProfileInsightVo> avoids = usable(profile == null || profile.summary() == null
                ? List.of() : profile.summary().avoids(), 2);

        LinkedHashSet<String> searchTerms = new LinkedHashSet<>();
        if (StringUtils.hasText(currentKeyword)) searchTerms.add(currentKeyword);
        likes.stream().map(MusicProfileInsightVo::value).forEach(searchTerms::add);
        String searchDescription = searchTerms.stream().limit(4)
                .reduce((left, right) -> left + " " + right).orElse("热门音乐");

        LinkedHashSet<String> playlistTerms = new LinkedHashSet<>();
        if (StringUtils.hasText(currentKeyword)) playlistTerms.add(currentKeyword);
        likes.stream().map(MusicProfileInsightVo::value).forEach(playlistTerms::add);

        String rationale = rationale(currentKeyword, likes, avoids, profile);
        String stage = profile == null || profile.summary() == null ? "EMPTY" : profile.summary().stage();
        return new RecommendationContext(true, !likes.isEmpty(), original, searchDescription,
                playlistTerms.stream().limit(2).toList(), rationale, stage);
    }

    private boolean isRecommendation(String request, MusicSearchIntent intent) {
        if (intent == MusicSearchIntent.DISCOVERY || intent == MusicSearchIntent.SIMILAR) return true;
        return intent == MusicSearchIntent.AMBIGUOUS && RECOMMENDATION_LANGUAGE.matcher(request).find();
    }

    private MusicProfileVo readProfile(long userId) {
        try {
            return personalizationService.profile(userId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<MusicProfileInsightVo> usable(List<MusicProfileInsightVo> values, int limit) {
        if (values == null || values.isEmpty()) return List.of();
        List<MusicProfileInsightVo> result = new ArrayList<>();
        for (MusicProfileInsightVo value : values) {
            if (value == null || !StringUtils.hasText(value.value())) continue;
            String normalized = MusicTextNormalizer.normalize(value.value());
            if (normalized.isEmpty() || "TRACK".equalsIgnoreCase(value.type())) {
                continue;
            }
            result.add(value);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }

    private static String meaningfulKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) return "";
        String value = keyword.strip();
        String normalized = MusicTextNormalizer.normalize(value);
        if (GENERIC_TERMS.stream().map(MusicTextNormalizer::normalize).anyMatch(normalized::equals)) return "";
        if (normalized.matches("(?:给我)?(?:推荐)?(?:一些|几个|点)?(?:歌单|歌曲|音乐)(?:推荐)?")) return "";
        if (RECOMMENDATION_LANGUAGE.matcher(value).find()) {
            String residue = GENERIC_REQUEST_WORDS.matcher(value).replaceAll(" ")
                    .replaceAll("(?:的)?(?:歌|歌单)?$", "")
                    .replaceAll("的$", "")
                    .replaceAll("[，。；,.;:：]+", " ")
                    .replaceAll("\\s{2,}", " ")
                    .strip();
            if (!StringUtils.hasText(residue)) return "";
            value = residue;
        }
        return value.length() > 60 ? value.substring(0, 60).strip() : value;
    }

    private static String rationale(String currentKeyword, List<MusicProfileInsightVo> likes,
                                    List<MusicProfileInsightVo> avoids, MusicProfileVo profile) {
        StringBuilder result = new StringBuilder();
        if (StringUtils.hasText(currentKeyword)) {
            result.append("优先满足你当前提出的“").append(currentKeyword).append("”需求");
        }
        if (!likes.isEmpty()) {
            if (!result.isEmpty()) result.append("，并");
            result.append("结合音乐画像中的偏好：").append(insightLabels(likes, 3));
        } else {
            if (!result.isEmpty()) result.append("；");
            String stage = profile == null || profile.summary() == null ? "" : profile.summary().stageLabel();
            result.append(StringUtils.hasText(stage) && !"暂无画像".equals(stage)
                    ? "当前画像证据仍较少，先使用探索性推荐"
                    : "当前还没有可靠画像，先使用热门内容进行冷启动推荐");
        }
        if (!avoids.isEmpty()) {
            result.append("；同时避开：").append(insightLabels(avoids, 2));
        }
        return result.append("。").toString();
    }

    private static String insightLabels(List<MusicProfileInsightVo> values, int limit) {
        return values.stream().limit(limit)
                .map(value -> value.typeLabel() + "“" + value.value() + "”")
                .reduce((left, right) -> left + "、" + right).orElse("");
    }

    public record RecommendationContext(
            boolean recommendation,
            boolean profileApplied,
            String originalRequest,
            String searchDescription,
            List<String> playlistKeywords,
            String rationale,
            String profileStage
    ) {
        public RecommendationContext {
            playlistKeywords = playlistKeywords == null ? List.of() : List.copyOf(playlistKeywords);
        }

        private static RecommendationContext literal(String request, String keyword) {
            return new RecommendationContext(false, false, request, request,
                    StringUtils.hasText(keyword) ? List.of(keyword) : List.of(), "", "NOT_USED");
        }
    }
}
