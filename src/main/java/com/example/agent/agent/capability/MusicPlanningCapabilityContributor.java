package com.example.agent.agent.capability;

import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.ValueType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fine-grained, typed capability contracts consumed by the future generic planner. */
@Component
public final class MusicPlanningCapabilityContributor implements AgentCapabilityContributor {
    private static final CapabilityPrecondition AUTHENTICATED = new CapabilityPrecondition(
            "authenticated-user", CapabilityPrecondition.Type.AUTHENTICATED_USER, true,
            "必须绑定当前登录用户");
    private static final CapabilityPrecondition PROFILE = new CapabilityPrecondition(
            "profile-available", CapabilityPrecondition.Type.PROFILE_AVAILABLE, true,
            "需要当前用户的只读画像快照");
    private static final CapabilityPrecondition QQ_SESSION = new CapabilityPrecondition(
            "qq-session", CapabilityPrecondition.Type.QQ_SESSION_AVAILABLE, false,
            "部分 QQ 音乐内容需要可用会话");
    private static final CapabilityPrecondition RECENT_RESULTS = new CapabilityPrecondition(
            "recent-results", CapabilityPrecondition.Type.RECENT_SEARCH_RESULTS, true,
            "需要当前会话最近一次已验收搜索结果");
    private static final CapabilityPrecondition EXPLICIT_INTENT = new CapabilityPrecondition(
            "explicit-intent", CapabilityPrecondition.Type.EXPLICIT_USER_INTENT, true,
            "用户必须明确要求执行状态操作");

    @Override
    public List<AgentCapabilityDefinition> capabilities() {
        return List.of(
                profileRead(), artistResolve(), trackSearch(), artistLookup(), playlistSearch(), chartRead(),
                playback(), queueAdd(), favoriteTrack(), recommendationFeedback(), goalAcceptance());
    }

    private static AgentCapabilityDefinition profileRead() {
        return capability("profile.music.read", "读取音乐画像", "读取当前用户可审计的音乐画像快照",
                Set.of("summarizeMusicProfile"), Set.of("画像", "偏好", "最常听"),
                CapabilitySchema.empty("profile.music.read.input.v1"),
                schema("profile.music.read.output.v1",
                        "stage", field(ValueType.STRING, true, "画像成熟阶段"),
                        "profileReady", field(ValueType.BOOLEAN, true, "画像是否达到可靠阈值"),
                        "topArtists", array(true, ValueType.ENTITY, "最常听歌手排行"),
                        "topTracks", array(true, ValueType.ENTITY, "最常听歌曲排行"),
                        "evidenceIds", array(true, ValueType.STRING, "画像证据标识")),
                List.of(AUTHENTICATED), CapabilitySideEffect.READ_ONLY, CapabilityConfirmationPolicy.NEVER,
                CapabilityExecutionPolicy.readOnly(10, 1, 1),
                CapabilityEvidencePolicy.read("PROFILE_CONTEXT", false, false));
    }

    private static AgentCapabilityDefinition artistResolve() {
        return capability("profile.artist.resolve", "解析偏好歌手", "从画像证据确定最偏好的歌手实体",
                Set.of(), Set.of("最喜欢的歌手", "最常听歌手"),
                schema("profile.artist.resolve.input.v1",
                        "profile", field(ValueType.OBJECT, true, "只读音乐画像")),
                schema("profile.artist.resolve.output.v1",
                        "artistName", field(ValueType.STRING, true, "解析后的歌手规范名称"),
                        "confidence", field(ValueType.DECIMAL, true, "解析置信度"),
                        "evidenceIds", array(true, ValueType.STRING, "支撑推断的画像证据")),
                List.of(AUTHENTICATED, PROFILE), CapabilitySideEffect.READ_ONLY,
                CapabilityConfirmationPolicy.NEVER, CapabilityExecutionPolicy.readOnly(3, 1, 1),
                CapabilityEvidencePolicy.read("PROFILE_ENTITY_RESOLUTION", false, true));
    }

    private static AgentCapabilityDefinition trackSearch() {
        return capability("music.track.search", "搜索或推荐歌曲", "按实体、场景或用户偏好查询真实歌曲",
                Set.of("recommendMusic"), Set.of("歌曲", "音乐", "推荐"),
                schema("music.track.search.input.v1",
                        "query", field(ValueType.STRING, true, "已解析的曲库查询"),
                        "limit", field(ValueType.INTEGER, false, "返回数量"),
                        "profile", field(ValueType.OBJECT, false, "可选画像快照")),
                schema("music.track.search.output.v1",
                        "searchId", field(ValueType.STRING, true, "搜索批次标识"),
                        "tracks", array(true, ValueType.ENTITY, "真实歌曲结果"),
                        "provider", field(ValueType.STRING, true, "结果来源")),
                List.of(AUTHENTICATED), CapabilitySideEffect.READ_ONLY, CapabilityConfirmationPolicy.NEVER,
                CapabilityExecutionPolicy.readOnly(20, 3, 2),
                CapabilityEvidencePolicy.read("SHOW_MUSIC_RESULTS", true, false));
    }

    private static AgentCapabilityDefinition artistLookup() {
        return capability("qq.artist.lookup", "查询 QQ 音乐艺人资料", "按明确歌手实体查询真实艺人档案",
                Set.of("searchQqArtists"), Set.of("歌手资料", "艺人档案"),
                schema("qq.artist.lookup.input.v1",
                        "artistName", field(ValueType.STRING, true, "已解析的歌手名称")),
                schema("qq.artist.lookup.output.v1",
                        "artistId", field(ValueType.STRING, true, "QQ 音乐艺人标识"),
                        "canonicalName", field(ValueType.STRING, true, "艺人规范名称"),
                        "profile", field(ValueType.OBJECT, true, "艺人资料"),
                        "provider", field(ValueType.STRING, true, "资料来源")),
                List.of(AUTHENTICATED, QQ_SESSION), CapabilitySideEffect.READ_ONLY,
                CapabilityConfirmationPolicy.NEVER, CapabilityExecutionPolicy.readOnly(20, 2, 2),
                CapabilityEvidencePolicy.read("SHOW_QQ_ARTIST_RESULTS", true, true));
    }

    private static AgentCapabilityDefinition playlistSearch() {
        return capability("qq.playlist.search", "搜索 QQ 音乐公开歌单", "按关键词、歌手或场景搜索公开歌单",
                Set.of("searchQqPlaylists"), Set.of("歌单", "播放列表"),
                schema("qq.playlist.search.input.v1",
                        "keyword", field(ValueType.STRING, true, "已解析的歌单关键词"),
                        "limit", field(ValueType.INTEGER, false, "返回数量")),
                schema("qq.playlist.search.output.v1",
                        "searchId", field(ValueType.STRING, true, "搜索批次标识"),
                        "playlists", array(true, ValueType.ENTITY, "公开歌单结果"),
                        "provider", field(ValueType.STRING, true, "结果来源")),
                List.of(AUTHENTICATED, QQ_SESSION), CapabilitySideEffect.READ_ONLY,
                CapabilityConfirmationPolicy.NEVER, CapabilityExecutionPolicy.readOnly(20, 2, 2),
                CapabilityEvidencePolicy.read("SHOW_QQ_PLAYLIST_RESULTS", true, false));
    }

    private static AgentCapabilityDefinition chartRead() {
        return capability("qq.chart.read", "读取 QQ 音乐榜单", "读取带来源、周期和排名依据的榜单结果",
                Set.of("queryQqMusicTrends"), Set.of("榜单", "排行", "热门", "趋势"),
                schema("qq.chart.read.input.v1",
                        "chartType", field(ValueType.STRING, true, "榜单或趋势类型"),
                        "window", field(ValueType.STRING, false, "统计周期"),
                        "artistName", field(ValueType.STRING, false, "可选歌手实体")),
                schema("qq.chart.read.output.v1",
                        "entries", array(true, ValueType.ENTITY, "排名条目"),
                        "source", field(ValueType.STRING, true, "榜单来源"),
                        "window", field(ValueType.STRING, true, "实际覆盖周期"),
                        "methodology", field(ValueType.STRING, true, "排名方法")),
                List.of(AUTHENTICATED), CapabilitySideEffect.READ_ONLY, CapabilityConfirmationPolicy.NEVER,
                CapabilityExecutionPolicy.readOnly(20, 2, 1),
                CapabilityEvidencePolicy.read("SHOW_QQ_CHART_RESULTS", true, false));
    }

    private static AgentCapabilityDefinition playback() {
        return capability("music.playback.play", "播放搜索结果", "播放当前会话已验收结果中的指定歌曲",
                Set.of("playRecommendedTrack"), Set.of("播放", "听这首"),
                schema("music.playback.play.input.v1",
                        "position", field(ValueType.INTEGER, true, "结果中的一基序号")),
                schema("music.playback.play.output.v1",
                        "success", field(ValueType.BOOLEAN, true, "是否开始播放"),
                        "track", entity(true, GoalTargetType.TRACK, "实际播放歌曲")),
                List.of(AUTHENTICATED, RECENT_RESULTS, EXPLICIT_INTENT),
                CapabilitySideEffect.REVERSIBLE_SESSION, CapabilityConfirmationPolicy.EXPLICIT_INTENT,
                CapabilityExecutionPolicy.mutation(10, 1), CapabilityEvidencePolicy.mutation("PLAY_TRACK"));
    }

    private static AgentCapabilityDefinition queueAdd() {
        return capability("music.queue.add", "加入播放队列", "把当前已验收歌曲结果加入可见播放队列",
                Set.of("queueLatestRecommendations"), Set.of("加入队列", "全部播放"),
                schema("music.queue.add.input.v1",
                        "tracks", array(true, ValueType.ENTITY, "待加入队列的歌曲")),
                schema("music.queue.add.output.v1",
                        "success", field(ValueType.BOOLEAN, true, "队列是否更新"),
                        "queuedCount", field(ValueType.INTEGER, true, "加入数量")),
                List.of(AUTHENTICATED, RECENT_RESULTS, EXPLICIT_INTENT),
                CapabilitySideEffect.REVERSIBLE_SESSION, CapabilityConfirmationPolicy.EXPLICIT_INTENT,
                CapabilityExecutionPolicy.mutation(10, 1),
                CapabilityEvidencePolicy.mutation("QUEUE_MUSIC_RESULTS"));
    }

    private static AgentCapabilityDefinition favoriteTrack() {
        return capability("music.track.favorite", "收藏歌曲", "收藏或取消收藏明确的歌曲实体",
                Set.of(), Set.of("收藏", "取消收藏", "喜欢这首"),
                schema("music.track.favorite.input.v1",
                        "track", entity(true, GoalTargetType.TRACK, "目标歌曲实体"),
                        "favorite", field(ValueType.BOOLEAN, true, "收藏或取消收藏")),
                schema("music.track.favorite.output.v1",
                        "success", field(ValueType.BOOLEAN, true, "收藏状态是否更新"),
                        "trackId", field(ValueType.STRING, true, "歌曲标识"),
                        "favorite", field(ValueType.BOOLEAN, true, "最终收藏状态")),
                List.of(AUTHENTICATED, EXPLICIT_INTENT), CapabilitySideEffect.PERSISTENT_MUTATION,
                CapabilityConfirmationPolicy.EXPLICIT_INTENT, CapabilityExecutionPolicy.mutation(10, 1),
                CapabilityEvidencePolicy.mutation("TRACK_FAVORITE_STATE"));
    }

    private static AgentCapabilityDefinition recommendationFeedback() {
        return capability("music.recommendation.feedback", "记录推荐反馈",
                "记录用户对最近推荐批次的明确反馈与持久偏好，并可触发后续推荐",
                Set.of(), Set.of("换一批", "不喜欢", "记住偏好", "推荐反馈"),
                schema("music.recommendation.feedback.input.v1",
                        "rejectLatestBatch", field(ValueType.BOOLEAN, true, "是否拒绝最近推荐批次"),
                        "preferences", array(true, ValueType.OBJECT, "用户明确表达的偏好变更"),
                        "recommendAgain", field(ValueType.BOOLEAN, true, "是否需要继续推荐"),
                        "recommendationRequest", field(ValueType.STRING, false, "结构化的后续推荐条件"),
                        "refreshBatch", field(ValueType.BOOLEAN, true, "是否避开最近曝光结果")),
                schema("music.recommendation.feedback.output.v1",
                        "success", field(ValueType.BOOLEAN, true, "反馈是否已应用"),
                        "rejectedTrackCount", field(ValueType.INTEGER, true, "写入负反馈的歌曲数"),
                        "acknowledgment", field(ValueType.STRING, true, "可验证的反馈确认")),
                List.of(AUTHENTICATED, EXPLICIT_INTENT), CapabilitySideEffect.PERSISTENT_MUTATION,
                CapabilityConfirmationPolicy.EXPLICIT_INTENT, CapabilityExecutionPolicy.mutation(10, 1),
                CapabilityEvidencePolicy.mutation("RECOMMENDATION_FEEDBACK_STATE"));
    }

    /** Logical verifier executed by the planning runtime, never a concrete tool implementation name. */
    private static AgentCapabilityDefinition goalAcceptance() {
        return new AgentCapabilityDefinition(
                "planner.goal.accept", "验收用户目标", "依据结构化验收条件检查目标任务输出",
                Set.of(), Set.of(), "module:generic-planning-internal", Set.of(), Set.of(), true,
                schema("planner.goal.accept.input.v1",
                        "goalId", field(ValueType.STRING, true, "被验收的用户目标"),
                        "result", field(ValueType.ANY, true, "目标实现任务的结构化输出"),
                        "criteria", array(true, ValueType.OBJECT, "机器可检查的验收条件")),
                schema("planner.goal.accept.output.v1",
                        "accepted", field(ValueType.BOOLEAN, true, "目标是否通过验收"),
                        "findings", array(true, ValueType.STRING, "验收发现")),
                List.of(AUTHENTICATED), CapabilitySideEffect.READ_ONLY,
                CapabilityConfirmationPolicy.NEVER, CapabilityExecutionPolicy.readOnly(5, 0, 1),
                CapabilityEvidencePolicy.read("GOAL_ACCEPTANCE", false, false));
    }

    private static AgentCapabilityDefinition capability(
            String id, String name, String description, Set<String> tools, Set<String> terms,
            CapabilitySchema input, CapabilitySchema output, List<CapabilityPrecondition> preconditions,
            CapabilitySideEffect sideEffect, CapabilityConfirmationPolicy confirmation,
            CapabilityExecutionPolicy execution, CapabilityEvidencePolicy evidence) {
        return new AgentCapabilityDefinition(id, name, description, tools, terms,
                "module:generic-planning", supportedOperations(id), supportedTargets(id),
                true, input, output, preconditions,
                sideEffect, confirmation, execution, evidence);
    }

    private static Set<GoalOperation> supportedOperations(String capabilityId) {
        return switch (capabilityId) {
            case "profile.music.read" -> Set.of(GoalOperation.LOOKUP, GoalOperation.ANALYZE,
                    GoalOperation.SUMMARIZE);
            case "profile.artist.resolve" -> Set.of(GoalOperation.RESOLVE);
            case "music.track.search" -> Set.of(GoalOperation.SEARCH, GoalOperation.RECOMMEND);
            case "music.recommendation.feedback" -> Set.of(GoalOperation.UPDATE);
            case "qq.artist.lookup" -> Set.of(GoalOperation.LOOKUP);
            case "qq.playlist.search" -> Set.of(GoalOperation.SEARCH, GoalOperation.RECOMMEND);
            case "qq.chart.read" -> Set.of(GoalOperation.SEARCH, GoalOperation.LOOKUP,
                    GoalOperation.ANALYZE);
            case "music.playback.play" -> Set.of(GoalOperation.PLAY);
            case "music.queue.add" -> Set.of(GoalOperation.QUEUE_ADD);
            case "music.track.favorite" -> Set.of(GoalOperation.UPDATE);
            default -> Set.of();
        };
    }

    private static Set<GoalTargetType> supportedTargets(String capabilityId) {
        return switch (capabilityId) {
            case "profile.music.read" -> Set.of(GoalTargetType.PROFILE);
            case "music.recommendation.feedback" -> Set.of(GoalTargetType.PROFILE);
            case "profile.artist.resolve", "qq.artist.lookup" -> Set.of(GoalTargetType.ARTIST);
            case "music.track.search", "music.track.favorite" -> Set.of(GoalTargetType.TRACK);
            case "qq.playlist.search" -> Set.of(GoalTargetType.PLAYLIST);
            case "qq.chart.read" -> Set.of(GoalTargetType.CHART);
            case "music.playback.play" -> Set.of(GoalTargetType.TRACK, GoalTargetType.SEARCH_RESULT);
            case "music.queue.add" -> Set.of(GoalTargetType.QUEUE);
            default -> Set.of();
        };
    }

    private static CapabilitySchema schema(String id, Object... pairs) {
        if (pairs.length % 2 != 0) throw new IllegalArgumentException("Schema 字段必须成对声明");
        java.util.LinkedHashMap<String, CapabilityFieldSchema> fields = new java.util.LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            fields.put(String.valueOf(pairs[index]), (CapabilityFieldSchema) pairs[index + 1]);
        }
        return CapabilitySchema.object(id, fields);
    }

    private static CapabilityFieldSchema field(ValueType type, boolean required, String description) {
        return required ? CapabilityFieldSchema.required(type, description)
                : CapabilityFieldSchema.optional(type, description);
    }

    private static CapabilityFieldSchema array(boolean required, ValueType itemType, String description) {
        return CapabilityFieldSchema.array(required, itemType, description);
    }

    private static CapabilityFieldSchema entity(boolean required, GoalTargetType type, String description) {
        return CapabilityFieldSchema.entity(required, type, description);
    }
}
