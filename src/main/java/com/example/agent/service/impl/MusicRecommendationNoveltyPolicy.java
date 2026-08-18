package com.example.agent.service.impl;

import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicTrackBo;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Conversation-scoped novelty policy. It is deterministic and never treats a refresh as negative taste. */
@Component
public class MusicRecommendationNoveltyPolicy {
    static final int HISTORY_BATCHES = 6;
    private static final int MAX_REFRESH_PAGE = 4;

    private final MusicPersonalizationRepository repository;

    public MusicRecommendationNoveltyPolicy(MusicPersonalizationRepository repository) {
        this.repository = repository;
    }

    public Context prepare(MusicRecommendationAo command, MusicExecutionPlan plan) {
        String fingerprint = fingerprint(plan);
        if (!command.personalizedRequest()) {
            return Context.standard(fingerprint, command.page());
        }
        int sequence = repository.nextBatchSequence(
                command.userId(), command.conversationId(), fingerprint);
        if (!command.refreshBatch() || !supportsRefresh(plan.intent())) {
            return new Context(fingerprint, sequence, false, command.page(), 0, Set.of(), Set.of());
        }

        List<MusicPersonalizationRepository.RecentExposureTrack> history = repository
                .recentExposureTracks(command.userId(), command.conversationId(), HISTORY_BATCHES);
        LinkedHashSet<String> trackKeys = new LinkedHashSet<>();
        LinkedHashSet<String> canonicalKeys = new LinkedHashSet<>();
        LinkedHashSet<String> exposureIds = new LinkedHashSet<>();
        for (var item : history) {
            trackKeys.add(item.trackKey());
            canonicalKeys.add(MusicTrackIdentity.canonicalKey(item.title(), item.primaryArtist()));
            exposureIds.add(item.exposureId());
        }
        int priorBatchCount = Math.max(exposureIds.size(), sequence - 1);
        int retrievalPage = Math.min(MAX_REFRESH_PAGE,
                Math.max(command.page(), command.page() + priorBatchCount));
        return new Context(fingerprint, sequence, true, retrievalPage, priorBatchCount,
                Set.copyOf(trackKeys), Set.copyOf(canonicalKeys));
    }

    public FilterResult filter(Context context, List<MusicTrackBo> candidates) {
        if (!context.refresh() || candidates == null || candidates.isEmpty()) {
            return new FilterResult(candidates == null ? List.of() : List.copyOf(candidates), 0);
        }
        List<MusicTrackBo> fresh = candidates.stream().filter(track -> !context.excludes(track)).toList();
        return new FilterResult(fresh, candidates.size() - fresh.size());
    }

    static String fingerprint(MusicExecutionPlan plan) {
        StringBuilder value = new StringBuilder(plan.intent().name()).append('|');
        append(value, plan.hardConstraints().track());
        plan.hardConstraints().artists().forEach(item -> append(value, item));
        append(value, plan.hardConstraints().album());
        plan.hints().genres().forEach(item -> append(value, item));
        plan.hints().moods().forEach(item -> append(value, item));
        plan.hints().scenes().forEach(item -> append(value, item));
        plan.hints().languages().forEach(item -> append(value, item));
        plan.toolCalls().forEach(call -> {
            value.append(call.name().name()).append(':');
            call.tasks().forEach(task -> append(value, task.query()));
        });
        return MusicTrackIdentity.sha256(value.toString());
    }

    private static void append(StringBuilder target, String value) {
        target.append(MusicTextNormalizer.normalize(value)).append('|');
    }

    private static boolean supportsRefresh(MusicSearchIntent intent) {
        return intent == MusicSearchIntent.DISCOVERY || intent == MusicSearchIntent.SIMILAR
                || intent == MusicSearchIntent.AMBIGUOUS;
    }

    public record Context(String requestFingerprint, int batchSequence, boolean refresh,
                          int retrievalPage, int recentBatchCount, Set<String> excludedTrackKeys,
                          Set<String> excludedCanonicalKeys) {
        public Context {
            excludedTrackKeys = excludedTrackKeys == null ? Set.of() : Set.copyOf(excludedTrackKeys);
            excludedCanonicalKeys = excludedCanonicalKeys == null
                    ? Set.of() : Set.copyOf(excludedCanonicalKeys);
        }

        static Context standard(String fingerprint, int page) {
            return new Context(fingerprint, 1, false, page, 0, Set.of(), Set.of());
        }

        public boolean excludes(MusicTrackBo track) {
            return excludedTrackKeys.contains(MusicTrackIdentity.key(track))
                    || excludedCanonicalKeys.contains(MusicTrackIdentity.canonicalKey(track));
        }
    }

    public record FilterResult(List<MusicTrackBo> tracks, int excludedCount) {
        public FilterResult {
            tracks = tracks == null ? List.of() : List.copyOf(tracks);
            excludedCount = Math.max(0, excludedCount);
        }
    }
}
