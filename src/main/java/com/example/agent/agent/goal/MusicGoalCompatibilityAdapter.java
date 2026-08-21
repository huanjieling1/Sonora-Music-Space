package com.example.agent.agent.goal;

import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.GoalConstraint;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Compatibility projection between the legacy scalar intent and a one-node goal graph. */
@Component
public final class MusicGoalCompatibilityAdapter {
    public UserGoalGraph fromIntent(String request, MusicIntentDraft intent) {
        if (intent == null) throw new IllegalArgumentException("旧意图不能为空");
        GoalOperation operation = operation(intent.action());
        GoalTargetType target = target(intent.target());
        List<GoalConstraint> constraints = intent.scenes().stream().map(scene -> new GoalConstraint(
                "scene", GoalConstraint.Operator.CONTAINS,
                ValueExpression.literal(ValueType.STRING, scene), true, "旧意图中的明确场景")).toList();
        Map<String, ValueExpression> inputs = intent.personalized()
                ? Map.of("profile", ValueExpression.profileValue(ValueType.OBJECT, "$.musicProfile")) : Map.of();
        GoalNode goal = new GoalNode("goal-1", title(operation, target), operation, target, inputs,
                constraints, List.of(new AcceptanceCriterion("goal-1-output",
                AcceptanceCriterion.Type.OUTPUT_PRESENT, "$.result", null, true,
                "兼容目标必须产生结果", Map.of())), intent.missingSlots(),
                operation == GoalOperation.PLAY || operation == GoalOperation.QUEUE_ADD);
        return new UserGoalGraph("1.0", UUID.randomUUID(), request, List.of(goal), List.of());
    }

    public Optional<MusicIntentDraft> toIntent(UserGoalGraph graph) {
        if (graph == null || graph.goals().size() != 1) return Optional.empty();
        GoalNode goal = graph.goals().get(0);
        List<String> scenes = goal.constraints().stream()
                .filter(value -> "scene".equals(value.field()))
                .map(GoalConstraint::expected)
                .filter(ValueExpression.Literal.class::isInstance)
                .map(ValueExpression.Literal.class::cast)
                .map(ValueExpression.Literal::value).filter(String.class::isInstance)
                .map(String.class::cast).toList();
        MusicIntentDraft.Action action = action(goal.operation());
        MusicIntentDraft.Target target = target(goal.targetType());
        MusicIntentDraft.Mode mode = action == MusicIntentDraft.Action.RECOMMEND
                ? MusicIntentDraft.Mode.DISCOVERY
                : action == MusicIntentDraft.Action.UNKNOWN ? MusicIntentDraft.Mode.UNKNOWN
                : MusicIntentDraft.Mode.EXACT;
        return Optional.of(new MusicIntentDraft(action, target, mode, MusicIntentDraft.RankingMetric.NONE,
                MusicIntentDraft.TimeWindow.UNSPECIFIED, scenes, goal.inputs().containsKey("profile"),
                goal.missingSlots(), 0.9, MusicIntentDraft.Domain.MUSIC));
    }

    private static GoalOperation operation(MusicIntentDraft.Action value) {
        return switch (value) {
            case RECOMMEND -> GoalOperation.RECOMMEND;
            case SEARCH -> GoalOperation.SEARCH;
            case PLAY -> GoalOperation.PLAY;
            case NAVIGATE -> GoalOperation.NAVIGATE;
            case QUEUE -> GoalOperation.QUEUE_ADD;
            case ANALYZE_PROFILE -> GoalOperation.ANALYZE;
            case CONVERSATION -> GoalOperation.RESPOND;
            default -> GoalOperation.UNKNOWN;
        };
    }

    private static MusicIntentDraft.Action action(GoalOperation value) {
        return switch (value) {
            case RECOMMEND -> MusicIntentDraft.Action.RECOMMEND;
            case SEARCH, LOOKUP, RESOLVE -> MusicIntentDraft.Action.SEARCH;
            case PLAY -> MusicIntentDraft.Action.PLAY;
            case NAVIGATE -> MusicIntentDraft.Action.NAVIGATE;
            case QUEUE_ADD, QUEUE_REMOVE -> MusicIntentDraft.Action.QUEUE;
            case ANALYZE, SUMMARIZE -> MusicIntentDraft.Action.ANALYZE_PROFILE;
            case RESPOND -> MusicIntentDraft.Action.CONVERSATION;
            default -> MusicIntentDraft.Action.UNKNOWN;
        };
    }

    private static GoalTargetType target(MusicIntentDraft.Target value) {
        return switch (value) {
            case TRACK -> GoalTargetType.TRACK;
            case PLAYLIST -> GoalTargetType.PLAYLIST;
            case ARTIST -> GoalTargetType.ARTIST;
            case ALBUM -> GoalTargetType.ALBUM;
            case PROFILE -> GoalTargetType.PROFILE;
            case SEARCH_RESULT -> GoalTargetType.SEARCH_RESULT;
            case QUEUE -> GoalTargetType.QUEUE;
            case CHART -> GoalTargetType.CHART;
            default -> GoalTargetType.NONE;
        };
    }

    private static MusicIntentDraft.Target target(GoalTargetType value) {
        return switch (value) {
            case TRACK -> MusicIntentDraft.Target.TRACK;
            case PLAYLIST -> MusicIntentDraft.Target.PLAYLIST;
            case ARTIST -> MusicIntentDraft.Target.ARTIST;
            case ALBUM -> MusicIntentDraft.Target.ALBUM;
            case PROFILE -> MusicIntentDraft.Target.PROFILE;
            case SEARCH_RESULT -> MusicIntentDraft.Target.SEARCH_RESULT;
            case QUEUE -> MusicIntentDraft.Target.QUEUE;
            case CHART -> MusicIntentDraft.Target.CHART;
            default -> MusicIntentDraft.Target.NONE;
        };
    }

    private static String title(GoalOperation operation, GoalTargetType target) {
        return operation.name().toLowerCase(java.util.Locale.ROOT) + " "
                + target.name().toLowerCase(java.util.Locale.ROOT);
    }
}
