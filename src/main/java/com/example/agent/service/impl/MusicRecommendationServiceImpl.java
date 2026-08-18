package com.example.agent.service.impl;

import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.ao.PreparedMusicRecommendationAo;
import com.example.agent.model.bo.MusicProviderStatusBo;
import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicMatchType;
import com.example.agent.model.bo.MusicExecutionPlan;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicSearchPlan;
import com.example.agent.model.bo.MusicSearchTask;
import com.example.agent.model.bo.MusicSearchTaskType;
import com.example.agent.model.bo.MusicSoftIntent;
import com.example.agent.model.bo.MusicStatusBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicUnderstandingBo;
import com.example.agent.model.bo.MusicToolName;
import com.example.agent.model.bo.MusicToolCall;
import com.example.agent.service.MusicCatalogProvider;
import com.example.agent.service.MusicQueryPlanner;
import com.example.agent.service.MusicRecommendationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import java.util.Set;

import static com.example.agent.service.impl.MusicCandidateRanker.Candidate;

@Service
public class MusicRecommendationServiceImpl implements MusicRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(MusicRecommendationServiceImpl.class);

    private final MusicQueryPlanner queryPlanner;
    private final MusicSearchPlanCompiler planCompiler;
    private final MusicCandidateVerifier candidateVerifier;
    private final MusicKnowledgeRepository feedbackRepository;
    private final MusicCandidateRanker candidateRanker;
    private final MusicPersonalizedRanker personalizedRanker;
    private final MusicPersonalizationRepository personalizationRepository;
    private final MusicRecommendationNoveltyPolicy noveltyPolicy;
    private final List<MusicCatalogProvider> providers;
    private final ExecutorService executor;
    private final int timeoutSeconds;

    @Autowired
    public MusicRecommendationServiceImpl(MusicQueryPlanner queryPlanner,
                                          MusicSearchPlanCompiler planCompiler,
                                          MusicCandidateVerifier candidateVerifier,
                                          MusicKnowledgeRepository feedbackRepository,
                                          MusicCandidateRanker candidateRanker,
                                          MusicPersonalizedRanker personalizedRanker,
                                          MusicPersonalizationRepository personalizationRepository,
                                          MusicRecommendationNoveltyPolicy noveltyPolicy,
                                          List<MusicCatalogProvider> providers,
                                          @Qualifier("musicProviderExecutor") ExecutorService executor,
                                          MusicCatalogProperties properties) {
        this.queryPlanner = queryPlanner;
        this.planCompiler = planCompiler;
        this.candidateVerifier = candidateVerifier;
        this.feedbackRepository = feedbackRepository;
        this.candidateRanker = candidateRanker;
        this.personalizedRanker = personalizedRanker;
        this.personalizationRepository = personalizationRepository;
        this.noveltyPolicy = noveltyPolicy;
        this.providers = providers.stream().sorted(Comparator.comparingInt(MusicCatalogProvider::order)).toList();
        this.executor = executor;
        this.timeoutSeconds = properties.resolvedTimeoutSeconds();
    }

    /** Test/backward-compatible constructor; production uses the fully injected constructor above. */
    MusicRecommendationServiceImpl(MusicQueryPlanner queryPlanner,
                                   MusicCandidateRanker candidateRanker,
                                   List<MusicCatalogProvider> providers,
                                   ExecutorService executor,
                                   MusicCatalogProperties properties) {
        this(queryPlanner, new MusicSearchPlanCompiler(), new MusicCandidateVerifier(), null,
                candidateRanker, null, null, null, providers, executor, properties);
    }

    MusicRecommendationServiceImpl(MusicQueryPlanner queryPlanner,
                                   MusicSearchPlanCompiler planCompiler,
                                   MusicCandidateVerifier candidateVerifier,
                                   MusicKnowledgeRepository feedbackRepository,
                                   MusicCandidateRanker candidateRanker,
                                   List<MusicCatalogProvider> providers,
                                   ExecutorService executor,
                                   MusicCatalogProperties properties) {
        this(queryPlanner, planCompiler, candidateVerifier, feedbackRepository, candidateRanker,
                null, null, null, providers, executor, properties);
    }

    @Override
    public MusicStatusBo status() {
        List<MusicProviderStatusBo> providerStatuses = providers.stream()
                .map(provider -> new MusicProviderStatusBo(provider.id(), provider.displayName(),
                        provider.configured(), provider.playbackTypes()))
                .toList();
        boolean ready = providers.stream().anyMatch(provider -> provider.configured() && !provider.fallbackOnly());
        String message = ready
                ? "音乐曲库已就绪"
                : "请导入 QQ 音乐登录态，或配置 Jamendo / Audius";
        return new MusicStatusBo(ready, providerStatuses, message);
    }

    @Override
    public MusicRecommendationBo recommend(MusicRecommendationAo command) {
        return recommendInternal(command, null);
    }

    @Override
    public MusicRecommendationBo recommendPrepared(PreparedMusicRecommendationAo prepared) {
        return recommendInternal(prepared.command(), prepared);
    }

    private MusicRecommendationBo recommendInternal(MusicRecommendationAo command,
                                                     PreparedMusicRecommendationAo prepared) {
        UUID exposureId = UUID.randomUUID();
        List<MusicCatalogProvider> directProviders = providers.stream()
                .filter(provider -> provider.configured() && !provider.fallbackOnly())
                .toList();
        if (directProviders.isEmpty()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "音乐曲库尚未配置，请导入 QQ 音乐登录态，或设置 Jamendo / Audius");
        }

        MusicSearchPlan proposedPlan = prepared != null && prepared.proposedPlan() != null
                ? prepared.proposedPlan() : queryPlanner.plan(command.description());
        PlanContext planContext = applyUserCorrections(command,
                planCompiler.compile(command.description(), proposedPlan));
        MusicExecutionPlan executionPlan = applyRecommendationProfile(
                planContext.executionPlan(), prepared);
        MusicRecommendationNoveltyPolicy.Context novelty = noveltyPolicy == null
                ? MusicRecommendationNoveltyPolicy.Context.standard("legacy", command.page())
                : noveltyPolicy.prepare(command, executionPlan);
        MusicUnderstandingBo understanding = planContext.understanding();
        MusicSearchPlan searchPlan = rankingPlan(executionPlan);
        MusicSearchTask primaryTask = executionPlan.tool(MusicToolName.QQ_DIRECT_SEARCH)
                .orElseThrow(() -> new IllegalStateException("Music execution plan has no direct search"))
                .tasks().get(0);
        String primaryQuery = primaryTask.query();
        List<MusicSearchTask> expansionTasks = executionPlan.tool(MusicToolName.QQ_EXPANDED_SEARCH)
                .map(call -> call.tasks()).orElse(List.of());
        int pageSize = command.pageSize();
        int candidatePoolSize = command.personalizedRequest()
                ? Math.min(30, Math.max(pageSize * 3, 10)) : pageSize;
        List<MusicCatalogProvider> qqProviders = directProviders.stream()
                .filter(provider -> "qq".equals(provider.id())).toList();
        List<MusicCatalogProvider> primaryProviders = qqProviders.isEmpty() ? directProviders : qqProviders;
        List<MusicCatalogProvider> supportingProviders = qqProviders.isEmpty() ? List.of()
                : directProviders.stream().filter(provider -> !"qq".equals(provider.id())).toList();

        List<ProviderSearchResult> primaryResults = searchAll(
                primaryProviders, List.of(primaryTask), novelty.retrievalPage(), pageSize);
        List<Candidate> directCandidates = candidateVerifier.verify(executionPlan, understanding,
                candidates(primaryResults), false);
        List<MusicTrackBo> directTracks = candidateRanker.rank(searchPlan, directCandidates, candidatePoolSize);
        LinkedHashMap<String, MusicTrackBo> tracks = new LinkedHashMap<>();
        addUnique(tracks, directTracks, candidatePoolSize);

        List<ProviderSearchResult> allResults = new ArrayList<>(primaryResults);
        List<MusicCatalogProvider> fallbackProviders = providers.stream()
                .filter(provider -> provider.configured() && provider.fallbackOnly())
                .toList();
        int remaining = candidatePoolSize - tracks.size();
        if (remaining > 0 && !expansionTasks.isEmpty()) {
            List<ProviderSearchResult> expanded = searchAll(primaryProviders, expansionTasks,
                    novelty.retrievalPage(), Math.min(10, Math.max(remaining, 3)));
            allResults.addAll(expanded);
            List<Candidate> verifiedExpansions = candidateVerifier.verify(executionPlan, understanding,
                    candidates(expanded), true);
            addUnique(tracks, candidateRanker.rank(searchPlan, verifiedExpansions,
                    Math.min(candidatePoolSize, remaining * 2)), candidatePoolSize);
            remaining = candidatePoolSize - tracks.size();
        }
        if (remaining > 0 && !supportingProviders.isEmpty()) {
            List<MusicSearchTask> supportTasks = executionPlan.tool(MusicToolName.OPEN_CATALOG_SEARCH)
                    .map(call -> call.tasks()).orElse(List.of(primaryTask));
            List<ProviderSearchResult> supporting = searchAll(supportingProviders,
                    supportTasks.stream().limit(3).toList(), novelty.retrievalPage(),
                    Math.min(10, Math.max(remaining, 3)));
            allResults.addAll(supporting);
            List<Candidate> verifiedSupporting = candidateVerifier.verify(executionPlan, understanding,
                    candidates(supporting), true);
            addUnique(tracks, candidateRanker.rank(searchPlan, verifiedSupporting,
                    Math.min(candidatePoolSize, remaining * 2)), candidatePoolSize);
            remaining = candidatePoolSize - tracks.size();
        }
        if (remaining > 0 && !fallbackProviders.isEmpty()) {
            List<ProviderSearchResult> fallback = searchAll(fallbackProviders, List.of(primaryTask),
                    novelty.retrievalPage(), Math.min(10, Math.max(remaining, 3)));
            allResults.addAll(fallback);
            List<Candidate> verifiedFallback = candidateVerifier.verify(executionPlan, understanding,
                    candidates(fallback), true);
            addUnique(tracks, candidateRanker.rank(searchPlan, verifiedFallback,
                    Math.min(candidatePoolSize, remaining * 2)), candidatePoolSize);
        }

        if (allResults.stream().noneMatch(ProviderSearchResult::success)) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "音乐曲库暂时无法访问，请稍后重试");
        }

        List<MusicTrackBo> rawCandidatePool = List.copyOf(tracks.values());
        MusicRecommendationNoveltyPolicy.FilterResult noveltyFilter = noveltyPolicy == null
                ? new MusicRecommendationNoveltyPolicy.FilterResult(rawCandidatePool, 0)
                : noveltyPolicy.filter(novelty, rawCandidatePool);
        List<MusicTrackBo> candidatePool = noveltyFilter.tracks();
        MusicPersonalizedRanker.RankingResult ranking;
        if (personalizedRanker != null) {
            Set<String> availableProviders = providers.stream().filter(MusicCatalogProvider::configured)
                    .map(MusicCatalogProvider::id).collect(java.util.stream.Collectors.toSet());
            ranking = personalizedRanker.rank(exposureId, command, executionPlan, candidatePool,
                    availableProviders, pageSize, novelty);
        } else {
            List<MusicTrackBo> legacy = candidatePool.stream().limit(pageSize).toList();
            ranking = new MusicPersonalizedRanker.RankingResult(legacy, List.of(), "baseline-v1",
                    MusicPersonalizationStatus.DISABLED);
        }
        List<MusicTrackBo> trackList = ranking.tracks();
        int verifiedCount = (int) trackList.stream()
                .filter(track -> track.matchType() == com.example.agent.model.bo.MusicMatchType.VERIFIED).count();
        int relatedCount = trackList.size() - verifiedCount;
        List<String> usedProviders = trackList.stream().map(MusicTrackBo::provider).distinct().toList();
        String searchQuery = displayQuery(allResults, primaryQuery);
        String explanation = explanation(searchPlan, searchQuery, trackList,
                usedProviders, verifiedCount, relatedCount);
        if (prepared != null && StringUtils.hasText(prepared.rationale())) {
            explanation = prepared.rationale() + " " + explanation;
        }
        explanation += personalizationExplanation(ranking.status());
        if (novelty.refresh()) {
            explanation += " 本次换批已避开当前会话最近 " + novelty.recentBatchCount()
                    + " 批展示过的歌曲。";
            if (trackList.size() < pageSize) {
                explanation += " 严格去重后只找到 " + trackList.size()
                        + " 首新歌曲，因此没有用旧结果凑满。";
            }
        }
        boolean hasNext = command.page() < MusicRecommendationAo.MAX_PAGE && trackList.size() == pageSize;
        if (command.personalizedRequest() && personalizationRepository != null) {
            personalizationRepository.recordExposure(command.userId(), command.conversationId(), exposureId,
                    command.description(), executionPlan, ranking.policyVersion(), ranking.status(),
                    novelty.requestFingerprint(), novelty.batchSequence(),
                    novelty.refresh() ? "REFRESH" : "STANDARD", ranking.exposureTracks());
        }
        return new MusicRecommendationBo(exposureId, command.description(), searchQuery, explanation,
                understanding, usedProviders, trackList, verifiedCount, relatedCount,
                command.page(), pageSize, hasNext, MusicRecommendationAo.MAX_PAGE,
                ranking.policyVersion(), ranking.status());
    }

    private static MusicExecutionPlan applyRecommendationProfile(
            MusicExecutionPlan plan, PreparedMusicRecommendationAo prepared) {
        if (prepared == null || plan.intent() != MusicSearchIntent.DISCOVERY
                || !hardConstraintsEmpty(plan) || !StringUtils.hasText(prepared.searchSeed())) {
            return plan;
        }

        MusicSearchTask primary = new MusicSearchTask(MusicSearchTaskType.SCENE,
                prepared.searchSeed(), null, null, null);
        LinkedHashMap<String, MusicSearchTask> expansions = new LinkedHashMap<>();
        plan.tool(MusicToolName.QQ_EXPANDED_SEARCH).stream().flatMap(call -> call.tasks().stream())
                .forEach(task -> expansions.putIfAbsent(MusicTextNormalizer.normalize(task.query()), task));
        prepared.preferredTerms().stream()
                .map(term -> new MusicSearchTask(MusicSearchTaskType.KEYWORDS, term, null, null, null))
                .forEach(task -> expansions.putIfAbsent(MusicTextNormalizer.normalize(task.query()), task));
        List<MusicSearchTask> expanded = expansions.values().stream()
                .filter(task -> !MusicTextNormalizer.normalize(task.query())
                        .equals(MusicTextNormalizer.normalize(primary.query())))
                .limit(3).toList();

        List<MusicToolCall> calls = new ArrayList<>();
        calls.add(new MusicToolCall("qq_direct", MusicToolName.QQ_DIRECT_SEARCH,
                List.of(primary), List.of()));
        if (!expanded.isEmpty()) {
            calls.add(new MusicToolCall("qq_expand", MusicToolName.QQ_EXPANDED_SEARCH,
                    expanded, List.of("qq_direct")));
        }
        List<MusicSearchTask> openTasks = new ArrayList<>();
        openTasks.add(primary);
        openTasks.addAll(expanded);
        calls.add(new MusicToolCall("open_catalog", MusicToolName.OPEN_CATALOG_SEARCH,
                openTasks.stream().limit(3).toList(),
                List.of(expanded.isEmpty() ? "qq_direct" : "qq_expand")));
        calls.add(new MusicToolCall("video_fallback", MusicToolName.VIDEO_FALLBACK_SEARCH,
                List.of(primary), List.of("open_catalog")));

        LinkedHashSet<String> avoids = new LinkedHashSet<>(plan.softIntent().avoid());
        String normalizedRequest = MusicTextNormalizer.normalize(plan.description());
        prepared.avoidedTerms().stream()
                .filter(term -> !normalizedRequest.contains(MusicTextNormalizer.normalize(term)))
                .forEach(avoids::add);
        return new MusicExecutionPlan(plan.description(), plan.intent(), plan.hardConstraints(),
                new MusicSoftIntent(plan.softIntent().goal(), List.copyOf(avoids)),
                plan.hints(), calls, plan.confidence(), plan.clarificationQuestion());
    }

    private static boolean hardConstraintsEmpty(MusicExecutionPlan plan) {
        return !StringUtils.hasText(plan.hardConstraints().track())
                && plan.hardConstraints().artists().isEmpty()
                && !StringUtils.hasText(plan.hardConstraints().album());
    }

    private List<ProviderSearchResult> searchAll(List<MusicCatalogProvider> selected,
                                                 List<MusicSearchTask> tasks, int page, int limit) {
        if (selected.isEmpty() || tasks == null || tasks.isEmpty() || limit <= 0) {
            return List.of();
        }
        AtomicInteger sequence = new AtomicInteger();
        List<CompletableFuture<ProviderSearchResult>> futures = selected.stream()
                .flatMap(provider -> tasks.stream().map(task -> {
                    int requestSequence = sequence.getAndIncrement();
                    return CompletableFuture
                            .supplyAsync(() -> search(provider, task, page, limit, requestSequence), executor)
                            .completeOnTimeout(ProviderSearchResult.failure(provider, task, requestSequence),
                                    timeoutSeconds, TimeUnit.SECONDS);
                }))
                .toList();
        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private ProviderSearchResult search(MusicCatalogProvider provider, MusicSearchTask task,
                                        int page, int limit, int sequence) {
        long startedAt = System.nanoTime();
        try {
            List<MusicTrackBo> tracks = provider.search(task, page, limit);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.info("Music provider {} task {} completed in {}ms with {} tracks",
                    provider.id(), task.type(), elapsedMs, tracks.size());
            return ProviderSearchResult.success(provider, task, tracks, sequence);
        } catch (RuntimeException exception) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            log.warn("Music provider {} task {} failed in {}ms: {}", provider.id(), task.type(), elapsedMs,
                    exception.getClass().getSimpleName());
            return ProviderSearchResult.failure(provider, task, sequence);
        }
    }

    private static List<Candidate> candidates(List<ProviderSearchResult> results) {
        AtomicInteger trackSequence = new AtomicInteger();
        return results.stream().filter(ProviderSearchResult::success)
                .flatMap(result -> result.tracks().stream().map(track -> new Candidate(
                        track, result.task(), result.providerOrder(),
                        result.sequence() * 1000 + trackSequence.getAndIncrement())))
                .toList();
    }

    private PlanContext applyUserCorrections(MusicRecommendationAo command, MusicExecutionPlan initial) {
        if (feedbackRepository == null || !command.personalizedRequest()) {
            MusicUnderstandingBo understanding = planCompiler.understanding(initial, List.of());
            return new PlanContext(initial, understanding);
        }
        try {
            var correction = feedbackRepository.findCorrection(command.userId(),
                    MusicTextNormalizer.normalize(initial.description()));
            if (correction.isPresent()) {
                var row = correction.get();
                MusicExecutionPlan corrected = planCompiler.withEntityCorrection(
                        initial, row.canonicalName(), row.entityType());
                List<String> aliases = feedbackRepository.aliasesFor(row.id());
                MusicUnderstandingBo understanding = new MusicUnderstandingBo(
                        row.id(), row.canonicalName(), row.entityType(), aliases, row.confidence(),
                        List.of("user_correction"), row.relatedTerms(), List.of(), List.of());
                return new PlanContext(corrected, understanding);
            }
            MusicUnderstandingBo provisional = planCompiler.understanding(initial, List.of());
            return new PlanContext(initial, provisional);
        } catch (RuntimeException exception) {
            log.warn("Music feedback lookup failed; continuing without stored corrections: {}",
                    exception.getClass().getSimpleName());
            return new PlanContext(initial, planCompiler.understanding(initial, List.of()));
        }
    }

    private static String personalizationExplanation(MusicPersonalizationStatus status) {
        return switch (status) {
            case ACTIVE -> " 个性化画像已参与有界重排。";
            case COLD_START -> " 当前处于画像冷启动，后续反馈会逐步改善排序。";
            case DEGRADED -> " 个性化依赖暂不可用，已安全降级且不影响曲库结果。";
            case DISABLED -> "";
        };
    }

    private static MusicSearchPlan rankingPlan(MusicExecutionPlan plan) {
        List<MusicSearchTask> tasks = plan.toolCalls().stream()
                .flatMap(call -> call.tasks().stream()).distinct().toList();
        return new MusicSearchPlan(plan.intent(), plan.hardConstraints().track(),
                plan.hardConstraints().artists(), plan.hardConstraints().album(),
                plan.hints().genres(), plan.hints().moods(), plan.hints().scenes(),
                tasks, plan.confidence(), plan.clarificationQuestion());
    }

    private static void addUnique(LinkedHashMap<String, MusicTrackBo> target, List<MusicTrackBo> candidates, int limit) {
        for (MusicTrackBo track : candidates) {
            if (target.size() >= limit) {
                return;
            }
            target.putIfAbsent(deduplicationKey(track), track);
        }
    }

    private static String deduplicationKey(MusicTrackBo track) {
        String artist = track.artists().isEmpty() ? "" : track.artists().get(0);
        return normalize(track.name()) + "|" + normalize(artist);
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String displayQuery(List<ProviderSearchResult> results, String primaryQuery) {
        if (results == null || results.isEmpty()) return primaryQuery;
        return results.stream().map(result -> result.task().query()).distinct().limit(3)
                .reduce((left, right) -> left + " / " + right).orElse("");
    }

    private String explanation(MusicSearchPlan plan, String searchQuery,
                               List<MusicTrackBo> tracks, List<String> usedProviders,
                               int verifiedCount, int relatedCount) {
        if (tracks.isEmpty()) {
            if (plan.intent() == MusicSearchIntent.AMBIGUOUS && plan.clarificationQuestion() != null) {
                return "没有找到可靠匹配。" + plan.clarificationQuestion();
            }
            return "开放曲库中暂时没有找到匹配歌曲，可以补充歌名、歌手、专辑或具体音乐风格。";
        }
        if (verifiedCount > 0 || relatedCount > 0) {
            return "音乐 Agent 使用“" + searchQuery + "”找到 " + verifiedCount + " 首严格匹配结果"
                    + (relatedCount > 0 ? "，并保留 " + relatedCount + " 首曲库候选或补充结果" : "")
                    + "。来源：" + providerNames(usedProviders) + "。";
        }
        return "音乐 Agent 识别为“" + intentName(plan.intent()) + "”，使用“" + searchQuery
                + "”检索，并从 " + providerNames(usedProviders) + " 找到 " + tracks.size() + " 首可播放歌曲。";
    }

    private static String intentName(MusicSearchIntent intent) {
        return switch (intent) {
            case EXACT_TRACK -> "精确歌曲";
            case ARTIST -> "歌手作品";
            case ALBUM -> "专辑";
            case ENTITY_RELATED -> "作品与实体相关音乐";
            case DISCOVERY -> "场景与风格推荐";
            case SIMILAR -> "相似音乐";
            case AMBIGUOUS -> "模糊实体";
        };
    }

    private String providerNames(List<String> ids) {
        return ids.stream()
                .map(id -> providers.stream().filter(provider -> provider.id().equals(id)).findFirst()
                        .map(MusicCatalogProvider::displayName).orElse(id))
                .reduce((left, right) -> left + "、" + right)
                .orElse("开放曲库");
    }

    private record ProviderSearchResult(String provider, int providerOrder, MusicSearchTask task,
                                        int sequence, boolean success, List<MusicTrackBo> tracks) {
        static ProviderSearchResult success(MusicCatalogProvider provider, MusicSearchTask task,
                                            List<MusicTrackBo> tracks, int sequence) {
            return new ProviderSearchResult(provider.id(), provider.order(), task, sequence,
                    true, tracks == null ? List.of() : tracks);
        }

        static ProviderSearchResult failure(MusicCatalogProvider provider, MusicSearchTask task, int sequence) {
            return new ProviderSearchResult(provider.id(), provider.order(), task, sequence, false, List.of());
        }
    }

    private record PlanContext(MusicExecutionPlan executionPlan, MusicUnderstandingBo understanding) {
    }
}
