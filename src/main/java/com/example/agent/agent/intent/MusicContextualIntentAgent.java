package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.service.impl.MusicAgentSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves pronouns and compound preference follow-ups against the latest verified result set. */
@Component
public class MusicContextualIntentAgent {
    private static final Logger log = LoggerFactory.getLogger(MusicContextualIntentAgent.class);
    private static final Pattern BATCH_REJECTION = Pattern.compile(
            "(?:不喜欢|不太喜欢|不想听|不合口味|不对胃口|不太对胃口).{0,12}(?:这些|这批|刚才|上批)"
                    + "|(?:这些|这批|刚才(?:推荐)?(?:的)?|上批).{0,12}(?:不喜欢|不太喜欢|不想听|不合口味|不对胃口|不太对胃口)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITIVE_ARTIST = Pattern.compile(
            "(?<!不)(?:更喜欢|比较喜欢|偏爱|喜欢)\\s*([^，。！？,.!?]{1,80}?)\\s*的(?:歌|歌曲|音乐|作品)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_ARTIST = Pattern.compile(
            "(?:不喜欢|讨厌|别再推荐|以后别推荐)\\s*([^，。！？,.!?]{1,80}?)\\s*的(?:歌|歌曲|音乐|作品)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOW_UP_CUE = Pattern.compile(
            "这些|这批|刚才|上批|换一批|重新推荐|再推荐|更喜欢|偏爱|不喜欢|不想听|不合口味|不对胃口",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY = Pattern.compile("换一批|重新推荐|再推荐|再来一批|重新来|换点", Pattern.CASE_INSENSITIVE);

    private final MusicAgentSessionStore sessionStore;
    private final MusicFollowUpPlanner planner;

    public MusicContextualIntentAgent(MusicAgentSessionStore sessionStore, MusicFollowUpPlanner planner) {
        this.sessionStore = sessionStore;
        this.planner = planner;
    }

    public Optional<MusicTurnPlan> analyze(MusicAgentTurn turn) {
        Optional<MusicRecommendationBo> latest = sessionStore.get(turn.memoryId());
        if (!FOLLOW_UP_CUE.matcher(turn.request()).find()) return Optional.empty();

        MusicTurnPlan deterministic = deterministic(turn.request(), latest.isPresent());
        if (deterministic.actionable()) return Optional.of(deterministic);

        try {
            MusicTurnPlan candidate = planner.plan(packet(turn.request(), latest.orElse(null)));
            MusicTurnPlan validated = validate(candidate, turn.request(), latest.isPresent());
            return validated.actionable() ? Optional.of(validated) : Optional.empty();
        } catch (RuntimeException exception) {
            log.debug("Contextual intent planning fell back to conversation: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    static MusicTurnPlan deterministic(String request, boolean hasLatest) {
        boolean rejectsBatch = BATCH_REJECTION.matcher(request).find();
        boolean retries = RETRY.matcher(request).find();
        List<MusicPreferenceChange> preferences = new ArrayList<>();

        Matcher positive = POSITIVE_ARTIST.matcher(request);
        if (positive.find()) {
            String artist = clean(positive.group(1));
            if (StringUtils.hasText(artist)) {
                preferences.add(new MusicPreferenceChange(MusicPreferenceType.ARTIST, artist, 1, true));
            }
        }
        if (!rejectsBatch) {
            Matcher negative = NEGATIVE_ARTIST.matcher(request);
            if (negative.find()) {
                String artist = clean(negative.group(1));
                if (StringUtils.hasText(artist)) {
                    preferences.add(new MusicPreferenceChange(MusicPreferenceType.ARTIST, artist, -1, true));
                }
            }
        }

        if (rejectsBatch && !hasLatest) {
            return new MusicTurnPlan(true, true, false, preferences, false, "", 1,
                    "你说的“这些”是指哪一批推荐？先让我给你生成一批结果，之后就能直接换掉。" );
        }
        boolean recommendAgain = retries || rejectsBatch || !preferences.isEmpty();
        String requestForRecommendation = recommendationRequest(preferences, recommendAgain);
        boolean actionable = rejectsBatch || retries || !preferences.isEmpty();
        return new MusicTurnPlan(actionable, rejectsBatch, rejectsBatch, preferences, recommendAgain,
                requestForRecommendation, retries || rejectsBatch, actionable ? 1 : 0, "");
    }

    private static MusicTurnPlan validate(MusicTurnPlan candidate, String request, boolean hasLatest) {
        if (candidate == null || !candidate.actionable() || candidate.confidence() < 0.65) {
            return MusicTurnPlan.none();
        }
        List<MusicPreferenceChange> safePreferences = candidate.preferences().stream()
                .filter(value -> value != null && value.value().length() <= 80)
                .filter(value -> literal(request).contains(literal(value.value())))
                .limit(3)
                .toList();
        boolean reject = candidate.rejectLatestBatch() && candidate.latestRecommendationReferenced() && hasLatest;
        if (candidate.rejectLatestBatch() && !hasLatest) {
            return new MusicTurnPlan(true, true, false, safePreferences, false, "", candidate.confidence(),
                    "你说的“这些”是指哪一批推荐？先让我给你生成一批结果，之后就能直接换掉。" );
        }
        boolean recommend = candidate.recommendAgain();
        String requestForRecommendation = candidate.recommendationRequest();
        if (recommend && !StringUtils.hasText(requestForRecommendation)) {
            requestForRecommendation = recommendationRequest(safePreferences, true);
        }
        boolean actionable = reject || !safePreferences.isEmpty() || recommend;
        boolean refreshBatch = RETRY.matcher(request).find() || reject;
        return new MusicTurnPlan(actionable, candidate.latestRecommendationReferenced(), reject,
                safePreferences, recommend, requestForRecommendation, refreshBatch, candidate.confidence(),
                candidate.clarificationQuestion());
    }

    private static String recommendationRequest(List<MusicPreferenceChange> preferences, boolean recommend) {
        if (!recommend) return "";
        return preferences.stream()
                .filter(value -> value.polarity() > 0 && value.type() == MusicPreferenceType.ARTIST)
                .findFirst()
                .map(value -> "推荐 " + value.value() + " 的歌")
                .orElse("根据我刚才的反馈重新推荐一些歌");
    }

    private static String packet(String request, MusicRecommendationBo latest) {
        StringBuilder result = new StringBuilder("当前用户原话：").append(request).append('\n');
        if (latest == null) return result.append("最近推荐：无").toString();
        result.append("最近推荐：有\n")
                .append("最近请求：").append(latest.description()).append('\n')
                .append("最近结果：\n");
        latest.tracks().stream().limit(10).forEach(track -> result.append("- ")
                .append(track.name()).append(" — ").append(String.join(" / ", track.artists())).append('\n'));
        return result.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("^(?:我|一些|点)", "").strip();
    }

    private static String literal(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\s，。！？,.!?]", "");
    }
}
