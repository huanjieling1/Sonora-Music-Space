package com.example.agent.orchestration.migration;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.contract.FavoriteArtistResolution;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.TypedEntityReference;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.execution.MusicExecutionAgent;
import com.example.agent.agent.feedback.MusicRecommendationFollowUpAgent;
import com.example.agent.agent.profile.FavoriteArtistResolver;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.QqArtistSearchResultBo;
import com.example.agent.model.bo.QqChartResultBo;
import com.example.agent.model.bo.QqPlaylistSearchResultBo;
import com.example.agent.orchestration.dag.DagTaskExecutionRequest;
import com.example.agent.orchestration.dag.DagTaskOutcome;
import com.example.agent.orchestration.dag.GenericDagTaskExecutor;
import com.example.agent.tools.AgentActionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Strangler bridge: capability IDs drive execution while old Route strategies remain implementation adapters. */
@Component
public final class MigratedMusicCapabilityExecutor implements GenericDagTaskExecutor {
    private final AgentCapabilityRegistry capabilities;
    private final MigratedMusicExecutionContextRegistry contexts;
    private final MusicExecutionAgent executionAgent;
    private final FavoriteArtistResolver artistResolver;
    private final MusicRecommendationFollowUpAgent followUpAgent;
    private final AgentActionContext actions;
    private final ObjectMapper objectMapper;
    private final MigratedMusicActionRegistry migratedActions;

    /** Compatibility constructor retained for focused executor tests. */
    public MigratedMusicCapabilityExecutor(AgentCapabilityRegistry capabilities,
                                           MigratedMusicExecutionContextRegistry contexts,
                                           MusicExecutionAgent executionAgent,
                                           FavoriteArtistResolver artistResolver,
                                           MusicRecommendationFollowUpAgent followUpAgent,
                                           AgentActionContext actions,
                                           ObjectMapper objectMapper) {
        this(capabilities, contexts, executionAgent, artistResolver, followUpAgent, actions,
                objectMapper, new MigratedMusicActionRegistry());
    }

    @Autowired
    public MigratedMusicCapabilityExecutor(AgentCapabilityRegistry capabilities,
                                           MigratedMusicExecutionContextRegistry contexts,
                                           MusicExecutionAgent executionAgent,
                                           FavoriteArtistResolver artistResolver,
                                           MusicRecommendationFollowUpAgent followUpAgent,
                                           AgentActionContext actions,
                                           ObjectMapper objectMapper,
                                           MigratedMusicActionRegistry migratedActions) {
        this.capabilities = capabilities;
        this.contexts = contexts;
        this.executionAgent = executionAgent;
        this.artistResolver = artistResolver;
        this.followUpAgent = followUpAgent;
        this.actions = actions;
        this.objectMapper = objectMapper;
        this.migratedActions = migratedActions;
    }

    @Override
    public DagTaskOutcome execute(DagTaskExecutionRequest request) {
        var context = contexts.require(request.workflowId(), request.principalId());
        String capability = request.task().capabilityId();
        if ("planner.goal.accept".equals(capability)) return accept(request);
        if ("profile.music.read".equals(capability)) return profile(request, context.tasteContext());
        if ("profile.artist.resolve".equals(capability)) return resolveArtist(request, context.tasteContext());
        if ("music.recommendation.feedback".equals(capability)) return feedback(request, context);
        if ("music.track.favorite".equals(capability)) {
            return DagTaskOutcome.failure("CAPABILITY_NOT_MIGRATED",
                    "收藏歌曲尚无旧功能实现可桥接", false);
        }

        MusicAgentRoute route = legacyRoute(capability);
        if (route == null) return DagTaskOutcome.failure("CAPABILITY_NOT_MIGRATED",
                "没有迁移能力执行器：" + capability, false);
        actions.begin(context.turn().memoryId());
        try {
            MusicAgentTurn taskTurn = new MusicAgentTurn(context.turn().userId(), context.turn().conversationId(),
                    executionRequest(capability, request.resolvedInputs()), refresh(request.resolvedInputs()));
            var legacy = executionAgent.execute(taskTurn, route, context.tasteContext());
            if (!legacy.successful()) return DagTaskOutcome.failure("LEGACY_EXECUTION_FAILED",
                    legacy.factualAnswer(), false);
            List<AgentActionBo> emitted = actions.actions();
            migratedActions.record(request.workflowId(), request.task().id(), emitted);
            return project(request, capability, emitted);
        } catch (RuntimeException exception) {
            return DagTaskOutcome.failure("LEGACY_CAPABILITY_EXCEPTION",
                    exception.getMessage() == null ? "旧能力桥接执行失败" : exception.getMessage(), true);
        } finally {
            actions.clear();
        }
    }

    private DagTaskOutcome profile(DagTaskExecutionRequest request, UserTasteContext taste) {
        UserTasteContext context = taste == null ? emptyProfile() : taste;
        List<Map<String, Object>> artists = context.topArtists().stream().map(item -> Map.<String, Object>of(
                "id", stableId("artist", item.name()), "name", item.name(), "count", item.count())).toList();
        List<Map<String, Object>> tracks = context.topTracks().stream().map(item -> Map.<String, Object>of(
                "id", stableId("track", item.name()), "name", item.name(), "count", item.count())).toList();
        List<String> evidence = new ArrayList<>();
        context.topArtists().forEach(item -> evidence.add(item.evidenceId()));
        context.topTracks().forEach(item -> evidence.add(item.evidenceId()));
        context.likes().forEach(item -> evidence.add(item.evidenceId()));
        if (evidence.isEmpty()) evidence.add("profile:" + request.principalId() + ":empty");
        List<TypedEntityReference> entities = context.topArtists().stream().map(item ->
                new TypedEntityReference(GoalTargetType.ARTIST, item.name(), "profile",
                        stableId("artist", item.name()))).toList();
        return success(request, Map.of("stage", context.stage(), "profileReady", context.profileReady(),
                "topArtists", artists, "topTracks", tracks, "evidenceIds", evidence),
                "profile", "profile:" + request.principalId(), entities, evidence);
    }

    private DagTaskOutcome resolveArtist(DagTaskExecutionRequest request, UserTasteContext taste) {
        FavoriteArtistResolution resolution = artistResolver.resolve(taste);
        if (!resolution.resolved()) {
            return DagTaskOutcome.waiting("favorite-artist.artistName", resolution.clarification());
        }
        String id = stableId("artist", resolution.artistName());
        return success(request, Map.of("artistName", resolution.artistName(),
                        "confidence", resolution.confidence(), "evidenceIds", resolution.evidenceIds()),
                "profile", id, List.of(new TypedEntityReference(GoalTargetType.ARTIST,
                        resolution.artistName(), "profile", id)), resolution.evidenceIds());
    }

    private DagTaskOutcome feedback(DagTaskExecutionRequest request,
                                    MigratedMusicExecutionContextRegistry.Context context) {
        MusicTurnPlan plan = feedbackPlan(request.resolvedInputs(), context.followUpPlan());
        var outcome = followUpAgent.apply(context.turn(), plan);
        return success(request, Map.of("success", true,
                        "rejectedTrackCount", outcome.rejectedTrackCount(),
                        "acknowledgment", outcome.acknowledgment()),
                "sonora-personalization", "feedback:" + request.workflowId(), List.of(),
                List.of("feedback:" + request.task().id() + ":" + request.idempotencyKey()));
    }

    private DagTaskOutcome accept(DagTaskExecutionRequest request) {
        Object result = request.resolvedInputs().get("result");
        boolean accepted = result instanceof Map<?, ?> map && !map.isEmpty();
        return success(request, Map.of("accepted", accepted,
                        "findings", accepted ? List.of() : List.of("目标结果为空")),
                "planner", request.task().goalIds().get(0), List.of(),
                List.of("goal-acceptance:" + request.task().id()));
    }

    private DagTaskOutcome project(DagTaskExecutionRequest request, String capability,
                                   List<AgentActionBo> emitted) {
        return switch (capability) {
            case "music.track.search" -> trackSearch(request, require(emitted, AgentActionType.SHOW_MUSIC_RESULTS));
            case "qq.artist.lookup" -> artist(request, require(emitted, AgentActionType.SHOW_QQ_ARTIST_RESULTS));
            case "qq.playlist.search" -> playlists(request,
                    require(emitted, AgentActionType.SHOW_QQ_PLAYLIST_RESULTS));
            case "qq.chart.read" -> chart(request, require(emitted, AgentActionType.SHOW_QQ_CHART_RESULTS));
            case "music.playback.play" -> playback(request, require(emitted, AgentActionType.PLAY_TRACK));
            case "music.queue.add" -> queue(request, require(emitted, AgentActionType.QUEUE_MUSIC_RESULTS));
            default -> DagTaskOutcome.failure("CAPABILITY_NOT_MIGRATED", capability, false);
        };
    }

    private DagTaskOutcome trackSearch(DagTaskExecutionRequest request, AgentActionBo action) {
        MusicRecommendationBo value = action.recommendation();
        List<TypedEntityReference> entities = value.tracks().stream().map(this::trackEntity).toList();
        String provider = value.providers().isEmpty() ? "music-catalog" : String.join(",", value.providers());
        return success(request, Map.of("searchId", String.valueOf(value.searchId()),
                        "tracks", value.tracks().stream().map(this::jsonMap).toList(), "provider", provider),
                provider, String.valueOf(value.searchId()), entities, evidence(action));
    }

    private DagTaskOutcome artist(DagTaskExecutionRequest request, AgentActionBo action) {
        QqArtistSearchResultBo search = action.artistSearch();
        if (search == null || search.artists().isEmpty()) {
            return DagTaskOutcome.failure("ARTIST_NOT_FOUND", "没有返回可验证艺人资料", false);
        }
        var value = search.artists().get(0);
        return success(request, Map.of("artistId", value.mid(), "canonicalName", value.name(),
                        "profile", jsonMap(value), "provider", "qq"), "qq", value.mid(),
                List.of(new TypedEntityReference(GoalTargetType.ARTIST, value.name(), "qq", value.mid())),
                evidence(action));
    }

    private DagTaskOutcome playlists(DagTaskExecutionRequest request, AgentActionBo action) {
        QqPlaylistSearchResultBo search = action.playlistSearch();
        List<TypedEntityReference> entities = search.playlists().stream().map(value ->
                new TypedEntityReference(GoalTargetType.PLAYLIST, value.name(), "qq", value.id())).toList();
        return success(request, Map.of("searchId", String.valueOf(search.searchId()),
                        "playlists", search.playlists().stream().map(this::jsonMap).toList(), "provider", "qq"),
                "qq", String.valueOf(search.searchId()), entities, evidence(action));
    }

    private DagTaskOutcome chart(DagTaskExecutionRequest request, AgentActionBo action) {
        QqChartResultBo chart = action.chartResult();
        List<?> entries = chart.officialChart() != null ? chart.officialChart().entries()
                : chart.trendReport().tracks().isEmpty() ? chart.trendReport().artists()
                : chart.trendReport().tracks();
        String window = chart.officialChart() != null ? chart.officialChart().chart().period()
                : chart.trendReport().window();
        String methodology = chart.officialChart() != null ? "QQ 音乐官方榜单顺序"
                : chart.trendReport().methodology();
        return success(request, Map.of("entries", entries.stream().map(this::jsonMap).toList(),
                        "source", "qq", "window", window, "methodology", methodology),
                "qq", "chart:" + action.id(), List.of(), evidence(action));
    }

    private DagTaskOutcome playback(DagTaskExecutionRequest request, AgentActionBo action) {
        MusicTrackBo track = action.track();
        return success(request, Map.of("success", true, "track", jsonMap(track)),
                provider(track.provider()), track.id(), List.of(trackEntity(track)), evidence(action));
    }

    private DagTaskOutcome queue(DagTaskExecutionRequest request, AgentActionBo action) {
        MusicRecommendationBo recommendation = action.recommendation();
        return success(request, Map.of("success", true, "queuedCount", recommendation.tracks().size()),
                "sonora-session", String.valueOf(recommendation.searchId()),
                recommendation.tracks().stream().map(this::trackEntity).toList(), evidence(action));
    }

    private DagTaskOutcome success(DagTaskExecutionRequest request, Map<String, Object> output,
                                   String provider, String resourceId,
                                   List<TypedEntityReference> entities, List<String> evidence) {
        var definition = capabilities.find(request.task().capabilityId())
                .orElseThrow(() -> new IllegalStateException("能力未注册：" + request.task().capabilityId()));
        return DagTaskOutcome.success(TypedTaskResult.success(request.task().id(), definition.outputSchema(),
                output, provider, resourceId, entities, evidence));
    }

    private static AgentActionBo require(List<AgentActionBo> actions, AgentActionType type) {
        return actions.stream().filter(action -> action.type() == type).reduce((left, right) -> right)
                .orElseThrow(() -> new IllegalStateException("旧能力没有产生预期证据：" + type));
    }

    private static MusicAgentRoute legacyRoute(String capability) {
        return switch (capability) {
            case "music.track.search" -> MusicAgentRoute.MUSIC_DISCOVERY;
            case "qq.artist.lookup" -> MusicAgentRoute.ARTIST_LOOKUP;
            case "qq.playlist.search" -> MusicAgentRoute.PLAYLIST_SEARCH;
            case "qq.chart.read" -> MusicAgentRoute.QQ_TREND_DISCOVERY;
            case "music.playback.play" -> MusicAgentRoute.RESULT_PLAYBACK;
            case "music.queue.add" -> MusicAgentRoute.QUEUE_CONTROL;
            default -> null;
        };
    }

    private static String executionRequest(String capability, Map<String, Object> inputs) {
        return switch (capability) {
            case "music.track.search" -> text(inputs, "query", "音乐推荐");
            case "qq.artist.lookup" -> text(inputs, "artistName", "未知歌手");
            case "qq.playlist.search" -> text(inputs, "keyword", "热门歌单");
            case "qq.chart.read" -> String.join(" ", List.of(text(inputs, "artistName", ""),
                    text(inputs, "chartType", "热门榜单"), text(inputs, "window", ""))).strip();
            case "music.playback.play" -> "播放第" + inputs.getOrDefault("position", 1) + "首";
            case "music.queue.add" -> "全部加入队列";
            default -> capability;
        };
    }

    private static boolean refresh(Map<String, Object> inputs) {
        return Boolean.TRUE.equals(inputs.get("refreshBatch"));
    }

    private static String text(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).strip();
    }

    private MusicTurnPlan feedbackPlan(Map<String, Object> inputs, MusicTurnPlan fallback) {
        List<MusicPreferenceChange> preferences = inputs.get("preferences") instanceof List<?> values
                ? values.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .map(this::preference).toList() : fallback.preferences();
        return new MusicTurnPlan(true, true, Boolean.TRUE.equals(inputs.get("rejectLatestBatch")), preferences,
                Boolean.TRUE.equals(inputs.get("recommendAgain")), text(inputs, "recommendationRequest", ""),
                Boolean.TRUE.equals(inputs.get("refreshBatch")), 1, "");
    }

    private MusicPreferenceChange preference(Map<?, ?> value) {
        return new MusicPreferenceChange(MusicPreferenceType.valueOf(String.valueOf(value.get("type"))),
                String.valueOf(value.get("value")), ((Number) value.get("polarity")).intValue(),
                Boolean.TRUE.equals(value.get("persistent")));
    }

    private TypedEntityReference trackEntity(MusicTrackBo track) {
        return new TypedEntityReference(GoalTargetType.TRACK, track.name(), provider(track.provider()), track.id());
    }

    private Map<String, Object> jsonMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static List<String> evidence(AgentActionBo action) {
        return List.of("action:" + action.type() + ":" + action.id());
    }

    private static String provider(String value) {
        return value == null || value.isBlank() ? "music-catalog" : value;
    }

    private static String stableId(String type, String name) {
        return type + ":" + UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static UserTasteContext emptyProfile() {
        return new UserTasteContext("EMPTY", "暂无画像", false, 0, 0, 0, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
