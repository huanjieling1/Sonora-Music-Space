package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.goal.MusicGoalDecomposer;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Keeps MusicAgentRoute as an input compatibility protocol; execution semantics live in Goal Graphs. */
@Component
public final class LegacyRouteGoalGraphAdapter {
    private static final Set<MusicAgentRoute> MIGRATED = Set.of(
            MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE, MusicAgentRoute.MUSIC_DISCOVERY,
            MusicAgentRoute.ARTIST_LOOKUP, MusicAgentRoute.PLAYLIST_SEARCH,
            MusicAgentRoute.QQ_TREND_DISCOVERY, MusicAgentRoute.RESULT_PLAYBACK,
            MusicAgentRoute.QUEUE_CONTROL, MusicAgentRoute.RECOMMENDATION_FOLLOW_UP,
            MusicAgentRoute.PROFILE_ANALYSIS);

    private final MusicGoalDecomposer decomposer;

    public LegacyRouteGoalGraphAdapter(MusicGoalDecomposer decomposer) {
        this.decomposer = decomposer;
    }

    public boolean migrated(MusicAgentRoute route) {
        return route != null && MIGRATED.contains(route);
    }

    public Optional<UserGoalGraph> adapt(MusicAgentTurn turn, MusicGoalUnderstanding understanding) {
        if (turn == null || understanding == null || !migrated(understanding.route())) return Optional.empty();
        if (understanding.route() == MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE) {
            return Optional.of(personalizedArtistProfile(turn.request()));
        }
        if (understanding.route() == MusicAgentRoute.RECOMMENDATION_FOLLOW_UP) {
            return Optional.of(feedback(turn.request(), understanding.followUpPlan()));
        }
        UserGoalGraph parsed = decomposer.decompose(turn.request());
        return Optional.of(enrich(parsed, understanding));
    }

    private static UserGoalGraph personalizedArtistProfile(String request) {
        GoalNode resolve = goal("favorite-artist", "从画像确定最偏好的歌手", GoalOperation.RESOLVE,
                GoalTargetType.ARTIST,
                Map.of("profile", ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile")), false,
                "$.artist");
        GoalNode dossier = goal("artist-profile", "查询该歌手的真实资料", GoalOperation.LOOKUP,
                GoalTargetType.ARTIST, Map.of(), false, "$.profile");
        return new UserGoalGraph("1.0", UUID.randomUUID(), request, List.of(resolve, dossier),
                List.of(new GoalRelation(resolve.id(), dossier.id(), GoalRelation.Type.DEPENDS_ON,
                        null, "歌手资料必须使用画像解析出的规范实体")));
    }

    private static UserGoalGraph feedback(String request, MusicTurnPlan source) {
        MusicTurnPlan plan = source == null ? MusicTurnPlan.none() : source;
        List<Map<String, Object>> preferences = plan.preferences().stream()
                .map(LegacyRouteGoalGraphAdapter::preference).toList();
        LinkedHashMap<String, ValueExpression> inputs = new LinkedHashMap<>();
        inputs.put("rejectLatestBatch", ValueExpression.literal(ValueType.BOOLEAN, plan.rejectLatestBatch()));
        inputs.put("preferences", ValueExpression.literal(ValueType.ARRAY, preferences));
        inputs.put("recommendAgain", ValueExpression.literal(ValueType.BOOLEAN, plan.recommendAgain()));
        inputs.put("refreshBatch", ValueExpression.literal(ValueType.BOOLEAN, plan.refreshBatch()));
        if (!plan.recommendationRequest().isBlank()) {
            inputs.put("recommendationRequest",
                    ValueExpression.literal(ValueType.STRING, plan.recommendationRequest()));
        }
        GoalNode feedback = goal("recommendation-feedback", "记录推荐反馈", GoalOperation.UPDATE,
                GoalTargetType.PROFILE, Map.copyOf(inputs), true, "$.success");
        ArrayList<GoalNode> goals = new ArrayList<>(List.of(feedback));
        ArrayList<GoalRelation> relations = new ArrayList<>();
        if (plan.recommendAgain()) {
            Map<String, ValueExpression> searchInputs = plan.recommendationRequest().isBlank() ? Map.of()
                    : Map.of("query", ValueExpression.literal(ValueType.STRING, plan.recommendationRequest()));
            GoalNode recommend = goal("feedback-recommendation", "按反馈重新推荐歌曲",
                    GoalOperation.RECOMMEND, GoalTargetType.TRACK, searchInputs, false, "$.tracks");
            goals.add(recommend);
            relations.add(new GoalRelation(feedback.id(), recommend.id(), GoalRelation.Type.SEQUENCE,
                    null, "先保存反馈，再使用新偏好推荐"));
        }
        return new UserGoalGraph("1.0", UUID.randomUUID(), request, goals, relations);
    }

    private static Map<String, Object> preference(MusicPreferenceChange value) {
        return Map.of("type", value.type().name(), "value", value.value(),
                "polarity", value.polarity(), "persistent", value.persistent());
    }

    private static UserGoalGraph enrich(UserGoalGraph graph, MusicGoalUnderstanding understanding) {
        List<GoalNode> goals = graph.goals().stream().map(goal -> {
            GoalOperation operation = switch (understanding.route()) {
                case MUSIC_DISCOVERY -> understanding.understanding().intent().action()
                        == com.example.agent.agent.contract.MusicIntentDraft.Action.SEARCH
                        ? GoalOperation.SEARCH : GoalOperation.RECOMMEND;
                case ARTIST_LOOKUP -> GoalOperation.LOOKUP;
                case PLAYLIST_SEARCH -> GoalOperation.SEARCH;
                case QQ_TREND_DISCOVERY -> GoalOperation.LOOKUP;
                case RESULT_PLAYBACK -> GoalOperation.PLAY;
                case QUEUE_CONTROL -> GoalOperation.QUEUE_ADD;
                case PROFILE_ANALYSIS -> GoalOperation.ANALYZE;
                default -> goal.operation();
            };
            GoalTargetType target = switch (understanding.route()) {
                case MUSIC_DISCOVERY -> GoalTargetType.TRACK;
                case ARTIST_LOOKUP -> GoalTargetType.ARTIST;
                case PLAYLIST_SEARCH -> GoalTargetType.PLAYLIST;
                case QQ_TREND_DISCOVERY -> GoalTargetType.CHART;
                case RESULT_PLAYBACK -> GoalTargetType.SEARCH_RESULT;
                case QUEUE_CONTROL -> GoalTargetType.QUEUE;
                case PROFILE_ANALYSIS -> GoalTargetType.PROFILE;
                default -> goal.targetType();
            };
            LinkedHashMap<String, ValueExpression> inputs = new LinkedHashMap<>(goal.inputs());
            if (understanding.route() == MusicAgentRoute.MUSIC_DISCOVERY
                    && understanding.understanding().intent().personalized()) {
                inputs.put("profile", ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile"));
            }
            boolean confirmation = operation == GoalOperation.PLAY || operation == GoalOperation.QUEUE_ADD;
            String subject = switch (target) {
                case TRACK -> "$.tracks";
                case ARTIST -> "$.profile";
                case PLAYLIST -> "$.playlists";
                case CHART -> "$.entries";
                case SEARCH_RESULT -> "$.track";
                case QUEUE -> "$.success";
                case PROFILE -> "$.stage";
                default -> "$.result";
            };
            AcceptanceCriterion.Type type = confirmation ? AcceptanceCriterion.Type.STATE_CHANGE
                    : AcceptanceCriterion.Type.OUTPUT_PRESENT;
            return new GoalNode(goal.id(), goal.title(), operation, target, inputs,
                    goal.constraints(), List.of(new AcceptanceCriterion(goal.id() + "-migration-output",
                    type, subject, null, true, "迁移目标必须产生可验收结果", Map.of())),
                    List.of(), confirmation);
        }).toList();
        return new UserGoalGraph(graph.schemaVersion(), graph.graphId(), graph.originalRequest(), goals,
                graph.relations());
    }

    private static GoalNode goal(String id, String title, GoalOperation operation, GoalTargetType target,
                                 Map<String, ValueExpression> inputs, boolean confirmation, String subject) {
        AcceptanceCriterion.Type type = confirmation ? AcceptanceCriterion.Type.STATE_CHANGE
                : AcceptanceCriterion.Type.OUTPUT_PRESENT;
        return new GoalNode(id, title, operation, target, inputs, List.of(),
                List.of(new AcceptanceCriterion(id + "-accepted", type, subject, null, true,
                        "迁移目标必须产生可验收结果", Map.of())), List.of(), confirmation);
    }
}
