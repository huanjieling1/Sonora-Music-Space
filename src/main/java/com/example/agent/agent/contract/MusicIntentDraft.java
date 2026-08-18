package com.example.agent.agent.contract;

import java.util.List;

/** Model-produced semantic slots. Java validation decides the executable route and capability boundary. */
public record MusicIntentDraft(
        Action action,
        Target target,
        Mode mode,
        RankingMetric rankingMetric,
        TimeWindow timeWindow,
        List<String> scenes,
        boolean personalized,
        List<String> missingSlots,
        double confidence,
        Domain domain
) {
    public MusicIntentDraft(Action action, Target target, Mode mode, RankingMetric rankingMetric,
                            TimeWindow timeWindow, List<String> scenes, boolean personalized,
                            List<String> missingSlots, double confidence) {
        this(action, target, mode, rankingMetric, timeWindow, scenes, personalized, missingSlots,
                confidence, Domain.MUSIC);
    }

    public MusicIntentDraft {
        action = action == null ? Action.UNKNOWN : action;
        target = target == null ? Target.NONE : target;
        mode = mode == null ? Mode.UNKNOWN : mode;
        rankingMetric = rankingMetric == null ? RankingMetric.NONE : rankingMetric;
        timeWindow = timeWindow == null ? TimeWindow.UNSPECIFIED : timeWindow;
        scenes = scenes == null ? List.of() : scenes.stream().filter(java.util.Objects::nonNull)
                .map(String::strip).filter(value -> !value.isEmpty()).distinct().limit(5).toList();
        missingSlots = missingSlots == null ? List.of() : missingSlots.stream()
                .filter(java.util.Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty())
                .distinct().limit(5).toList();
        confidence = Double.isFinite(confidence) ? Math.max(0, Math.min(1, confidence)) : 0;
        domain = domain == null ? Domain.UNKNOWN : domain;
    }

    public static MusicIntentDraft unknown() {
        return new MusicIntentDraft(Action.UNKNOWN, Target.NONE, Mode.UNKNOWN, RankingMetric.NONE,
                TimeWindow.UNSPECIFIED, List.of(), false, List.of(), 0, Domain.UNKNOWN);
    }

    public enum Action {
        RECOMMEND, SEARCH, PLAY, NAVIGATE, QUEUE, ANALYZE_PROFILE, CAPABILITY_INQUIRY,
        CONVERSATION, UNKNOWN
    }

    public enum Target {
        TRACK, PLAYLIST, ARTIST, ALBUM, PROFILE, SEARCH_RESULT, QUEUE, CHART, NONE
    }

    public enum Mode {
        EXACT, DISCOVERY, TRENDING, RANDOM, FOLLOW_UP, UNKNOWN
    }

    public enum RankingMetric {
        HOTNESS, RISING, NEWNESS, RELEVANCE, NONE
    }

    public enum TimeWindow {
        REALTIME, DAY, WEEK, MONTH, RECENT, ALL_TIME, UNSPECIFIED
    }

    public enum Domain {
        MUSIC, SOCIAL, OTHER, UNKNOWN
    }
}
