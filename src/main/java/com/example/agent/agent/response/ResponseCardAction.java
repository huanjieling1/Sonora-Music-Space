package com.example.agent.agent.response;

import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.TypedEntityReference;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Generic front-end card action generated only from an accepted typed task result. */
public record ResponseCardAction(
        UUID id,
        Type type,
        String goalId,
        String sourceTaskId,
        GoalTargetType targetType,
        String provider,
        String resourceId,
        Map<String, Object> payload,
        List<TypedEntityReference> entities,
        List<String> evidenceIds
) {
    public ResponseCardAction {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? Type.GENERIC_RESULT : type;
        if (goalId == null || goalId.isBlank()) throw new IllegalArgumentException("卡片 Action 必须关联目标");
        if (sourceTaskId == null || sourceTaskId.isBlank()) throw new IllegalArgumentException("卡片 Action 必须关联任务");
        goalId = goalId.strip();
        sourceTaskId = sourceTaskId.strip();
        targetType = targetType == null ? GoalTargetType.NONE : targetType;
        provider = provider == null ? "" : provider.strip();
        resourceId = resourceId == null ? "" : resourceId.strip();
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        entities = entities == null ? List.of() : List.copyOf(entities);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }

    public enum Type {
        TRACK_RESULTS,
        ARTIST_RESULT,
        PLAYLIST_RESULTS,
        CHART_RESULT,
        PROFILE_RESULT,
        PLAYBACK_STATE,
        QUEUE_STATE,
        FAVORITE_STATE,
        GENERIC_RESULT
    }
}
