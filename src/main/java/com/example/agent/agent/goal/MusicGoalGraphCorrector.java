package com.example.agent.agent.goal;

import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalRelation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Literal-grounding authority for model-produced goal graphs. */
@Component
public final class MusicGoalGraphCorrector {
    private static final Set<String> EXPLICIT_INPUTS = Set.of(
            "artistName", "trackTitle", "albumName", "playlistName", "limit", "position");
    private static final Set<String> ENTITY_INPUTS = Set.of(
            "artistName", "trackTitle", "albumName", "playlistName");

    public UserGoalGraph correct(String request, UserGoalGraph candidate, UserGoalGraph fallback) {
        String original = requireRequest(request);
        if (candidate == null || candidate.goals().isEmpty() || candidate.goals().size() > 12) return fallback;
        if (fallback == null || fallback.goals().isEmpty()) return groundedCandidate(original, candidate);

        ArrayList<GoalNode> goals = new ArrayList<>();
        LinkedHashMap<String, String> fallbackToMerged = new LinkedHashMap<>();
        LinkedHashSet<String> usedCandidates = new LinkedHashSet<>();
        for (int index = 0; index < fallback.goals().size(); index++) {
            GoalNode literal = fallback.goals().get(index);
            GoalNode proposed = bestMatch(candidate.goals(), literal, index, usedCandidates);
            if (proposed == null) proposed = literal;
            GoalNode merged = mergeGoal(original, proposed, literal);
            goals.add(merged);
            usedCandidates.add(proposed.id());
            fallbackToMerged.put(literal.id(), merged.id());
        }
        candidate.goals().stream().filter(value -> !usedCandidates.contains(value.id()))
                .filter(MusicGoalGraphCorrector::usableExtraGoal)
                .map(value -> safeExtraGoal(original, value))
                .limit(Math.max(0, 12 - goals.size()))
                .forEach(goals::add);

        LinkedHashSet<GoalRelation> relations = new LinkedHashSet<>();
        Set<String> ids = goals.stream().map(GoalNode::id).collect(java.util.stream.Collectors.toSet());
        candidate.relations().stream().filter(value -> valid(value, ids))
                .filter(value -> acyclicAfterAdding(relations, value)).forEach(relations::add);
        for (GoalRelation relation : fallback.relations()) {
            String source = fallbackToMerged.get(relation.sourceGoalId());
            String target = fallbackToMerged.get(relation.targetGoalId());
            if (source != null && target != null && !source.equals(target)) {
                GoalRelation mapped = new GoalRelation(source, target, relation.type(), relation.condition(),
                        relation.description());
                if (acyclicAfterAdding(relations, mapped)) relations.add(mapped);
            }
        }
        return new UserGoalGraph("1.0", candidate.graphId(), original, goals, List.copyOf(relations));
    }

    private static GoalNode bestMatch(List<GoalNode> candidates, GoalNode literal, int fallbackIndex,
                                      Set<String> used) {
        GoalNode exact = candidates.stream().filter(value -> !used.contains(value.id()))
                .filter(value -> value.operation() == literal.operation()
                        && value.targetType() == literal.targetType()).findFirst().orElse(null);
        if (exact != null) return exact;
        GoalNode sameTarget = candidates.stream().filter(value -> !used.contains(value.id()))
                .filter(value -> value.targetType() == literal.targetType()).findFirst().orElse(null);
        if (sameTarget != null) return sameTarget;
        if (fallbackIndex < candidates.size() && !used.contains(candidates.get(fallbackIndex).id())) {
            return candidates.get(fallbackIndex);
        }
        return candidates.stream().filter(value -> !used.contains(value.id())).findFirst().orElse(null);
    }

    private static boolean usableExtraGoal(GoalNode value) {
        return value != null && value.operation() != GoalOperation.UNKNOWN
                && value.targetType() != GoalTargetType.NONE && !value.acceptanceCriteria().isEmpty();
    }

    private static GoalNode safeExtraGoal(String request, GoalNode value) {
        GoalNode grounded = removeInventedEntities(request, value);
        boolean removedEntity = ENTITY_INPUTS.stream().anyMatch(key -> value.inputs().containsKey(key)
                && !grounded.inputs().containsKey(key));
        if (!removedEntity) return grounded;
        return new GoalNode(grounded.id(), grounded.operation().name() + " " + grounded.targetType().name(),
                grounded.operation(), grounded.targetType(), grounded.inputs(), grounded.constraints(),
                grounded.acceptanceCriteria(), grounded.missingSlots(), grounded.requiresConfirmation());
    }

    private static UserGoalGraph groundedCandidate(String request, UserGoalGraph candidate) {
        List<GoalNode> goals = candidate.goals().stream().map(value -> removeInventedEntities(request, value)).toList();
        Set<String> ids = goals.stream().map(GoalNode::id).collect(java.util.stream.Collectors.toSet());
        List<GoalRelation> relations = candidate.relations().stream().filter(value -> valid(value, ids)).toList();
        return new UserGoalGraph("1.0", candidate.graphId(), request, goals, relations);
    }

    private static GoalNode mergeGoal(String request, GoalNode proposed, GoalNode literal) {
        GoalNode grounded = removeInventedEntities(request, proposed);
        LinkedHashMap<String, ValueExpression> inputs = new LinkedHashMap<>(grounded.inputs());
        literal.inputs().forEach((key, value) -> {
            if (EXPLICIT_INPUTS.contains(key)) inputs.put(key, value);
        });
        ArrayList<String> missing = new ArrayList<>(grounded.missingSlots());
        literal.missingSlots().stream().filter(value -> !missing.contains(value)).forEach(missing::add);
        GoalOperation operation = literal.operation() == GoalOperation.UNKNOWN
                ? grounded.operation() : literal.operation();
        GoalTargetType target = literal.targetType() == GoalTargetType.NONE
                ? grounded.targetType() : literal.targetType();
        return new GoalNode(grounded.id(), literal.title(), operation, target, inputs,
                literal.constraints().isEmpty() ? grounded.constraints() : literal.constraints(),
                literal.acceptanceCriteria().isEmpty() ? grounded.acceptanceCriteria() : literal.acceptanceCriteria(),
                missing, literal.requiresConfirmation() || grounded.requiresConfirmation());
    }

    private static GoalNode removeInventedEntities(String request, GoalNode goal) {
        LinkedHashMap<String, ValueExpression> inputs = new LinkedHashMap<>(goal.inputs());
        ArrayList<String> missing = new ArrayList<>(goal.missingSlots());
        for (String key : ENTITY_INPUTS) {
            ValueExpression expression = inputs.get(key);
            if (expression instanceof ValueExpression.Literal literal
                    && literal.value() instanceof String value && !request.contains(value)) {
                inputs.remove(key);
                if (!missing.contains(key)) missing.add(key);
            }
        }
        return new GoalNode(goal.id(), goal.title(), goal.operation(), goal.targetType(), inputs,
                goal.constraints(), goal.acceptanceCriteria(), missing, goal.requiresConfirmation());
    }

    private static boolean valid(GoalRelation relation, Set<String> ids) {
        return relation != null && ids.contains(relation.sourceGoalId())
                && ids.contains(relation.targetGoalId())
                && !relation.sourceGoalId().equals(relation.targetGoalId());
    }

    private static boolean acyclicAfterAdding(Set<GoalRelation> existing, GoalRelation candidate) {
        if (candidate.type() == GoalRelation.Type.PARALLEL) return true;
        LinkedHashMap<String, Set<String>> edges = new LinkedHashMap<>();
        java.util.stream.Stream.concat(existing.stream(), java.util.stream.Stream.of(candidate))
                .filter(value -> value.type() != GoalRelation.Type.PARALLEL)
                .forEach(value -> edges.computeIfAbsent(value.sourceGoalId(), ignored -> new LinkedHashSet<>())
                        .add(value.targetGoalId()));
        return !reaches(edges, candidate.targetGoalId(), candidate.sourceGoalId(), new LinkedHashSet<>());
    }

    private static boolean reaches(Map<String, Set<String>> edges, String current, String target,
                                   Set<String> visited) {
        if (current.equals(target)) return true;
        if (!visited.add(current)) return false;
        return edges.getOrDefault(current, Set.of()).stream()
                .anyMatch(next -> reaches(edges, next, target, visited));
    }

    private static String requireRequest(String request) {
        if (request == null || request.isBlank()) throw new IllegalArgumentException("用户请求不能为空");
        return request.strip();
    }
}
