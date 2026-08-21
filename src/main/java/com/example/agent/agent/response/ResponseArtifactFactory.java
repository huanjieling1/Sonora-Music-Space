package com.example.agent.agent.response;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedEntityReference;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Converts accepted typed results into deterministic facts and generic UI actions. */
@Component
public final class ResponseArtifactFactory {
    private final AgentCapabilityRegistry registry;

    public ResponseArtifactFactory(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public GroundedResponseFact fact(GoalNode goal, PlanTask task, TypedTaskResult result) {
        GroundedResponseFact.Kind kind = factKind(task);
        String statement = statement(goal, task, result, kind);
        return new GroundedResponseFact(goal.id(), task.id(), kind, statement,
                result.evidenceIds(), result.entities().stream().map(TypedEntityReference::entityId).toList());
    }

    public ResponseCardAction action(GoalNode goal, PlanTask task, TypedTaskResult result) {
        UUID actionId = UUID.nameUUIDFromBytes((goal.id() + ":" + task.id() + ":"
                + String.join(",", result.evidenceIds())).getBytes(StandardCharsets.UTF_8));
        return new ResponseCardAction(actionId, actionType(goal), goal.id(), task.id(),
                goal.targetType(), result.provider(), result.resourceId(), safePayload(result),
                result.entities(), result.evidenceIds());
    }

    /** Card payload never copies arbitrary model/provider entity text; identities live in typed entities. */
    private static Map<String, Object> safePayload(TypedTaskResult result) {
        if (!(result.output() instanceof Map<?, ?> output)) return Map.of();
        LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
        copyScalar(output, safe, "queuedCount", Number.class);
        copyScalar(output, safe, "favorite", Boolean.class);
        copyScalar(output, safe, "profileReady", Boolean.class);
        copyScalar(output, safe, "stage", String.class);
        copyScalar(output, safe, "window", String.class);
        for (String collection : List.of("tracks", "playlists", "entries")) {
            if (output.get(collection) instanceof Collection<?> values) {
                safe.put("itemCount", values.size());
                break;
            }
        }
        return Map.copyOf(safe);
    }

    private static void copyScalar(Map<?, ?> source, Map<String, Object> target,
                                   String key, Class<?> type) {
        Object value = source.get(key);
        if (type.isInstance(value)) target.put(key, value);
    }

    private GroundedResponseFact.Kind factKind(PlanTask task) {
        AgentCapabilityDefinition capability = registry.find(task.capabilityId()).orElse(null);
        if (capability != null && capability.sideEffect() != CapabilitySideEffect.READ_ONLY) {
            return GroundedResponseFact.Kind.STATE_CHANGE;
        }
        if (task.capabilityId().startsWith("profile.")) return GroundedResponseFact.Kind.INFERENCE;
        return GroundedResponseFact.Kind.EXTERNAL_FACT;
    }

    private static String statement(GoalNode goal, PlanTask task, TypedTaskResult result,
                                    GroundedResponseFact.Kind kind) {
        Map<?, ?> output = result.output() instanceof Map<?, ?> value ? value : Map.of();
        String entities = result.entities().stream().map(TypedEntityReference::canonicalName)
                .distinct().limit(5).collect(Collectors.joining("、"));
        if (kind == GroundedResponseFact.Kind.INFERENCE) {
            if (!entities.isBlank()) return "根据已验收的画像证据，推断结果为：" + entities + "。";
            if (output.get("stage") != null) return "根据已验收的画像证据，当前画像阶段为 " + output.get("stage") + "。";
            return "已根据用户画像证据完成“" + goal.title() + "”的推断。";
        }
        if (kind == GroundedResponseFact.Kind.STATE_CHANGE) {
            if (task.capabilityId().equals("music.queue.add")) {
                return "队列状态已更新，实际加入 " + number(output.get("queuedCount")) + " 首。";
            }
            if (task.capabilityId().equals("music.playback.play")) {
                return entities.isBlank() ? "播放状态已由执行证据确认更新。" : "已开始播放：" + entities + "。";
            }
            if (task.capabilityId().equals("music.track.favorite")) {
                return Boolean.TRUE.equals(output.get("favorite")) ? "收藏状态已更新为已收藏。"
                        : "收藏状态已更新为未收藏。";
            }
            return "“" + goal.title() + "”的状态变更已由执行证据确认。";
        }
        if (output.get("tracks") instanceof Collection<?> tracks) {
            return collectionStatement("真实曲库返回 " + tracks.size() + " 首歌曲", entities);
        }
        if (output.get("playlists") instanceof Collection<?> playlists) {
            return collectionStatement("外部曲库返回 " + playlists.size() + " 个歌单", entities);
        }
        if (output.get("entries") instanceof Collection<?> entries) {
            String suffix = output.get("window") == null ? "" : "，统计周期为 " + output.get("window");
            return "外部榜单返回 " + entries.size() + " 条结果" + suffix + "。";
        }
        if (!entities.isBlank()) return "外部数据源确认实体：" + entities + "。";
        return "“" + goal.title() + "”已产生带来源和证据的结构化结果。";
    }

    private static String collectionStatement(String prefix, String entities) {
        return entities.isBlank() ? prefix + "。" : prefix + "，已验证实体包括：" + entities + "。";
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static ResponseCardAction.Type actionType(GoalNode goal) {
        if (goal.operation() == GoalOperation.PLAY) return ResponseCardAction.Type.PLAYBACK_STATE;
        if (goal.operation() == GoalOperation.QUEUE_ADD || goal.operation() == GoalOperation.QUEUE_REMOVE) {
            return ResponseCardAction.Type.QUEUE_STATE;
        }
        if (goal.operation() == GoalOperation.UPDATE && goal.targetType() == GoalTargetType.TRACK) {
            return ResponseCardAction.Type.FAVORITE_STATE;
        }
        return switch (goal.targetType()) {
            case TRACK, SEARCH_RESULT -> ResponseCardAction.Type.TRACK_RESULTS;
            case ARTIST -> ResponseCardAction.Type.ARTIST_RESULT;
            case PLAYLIST -> ResponseCardAction.Type.PLAYLIST_RESULTS;
            case CHART -> ResponseCardAction.Type.CHART_RESULT;
            case PROFILE -> ResponseCardAction.Type.PROFILE_RESULT;
            default -> ResponseCardAction.Type.GENERIC_RESULT;
        };
    }
}
