package com.example.agent.agent.evaluation;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedEntityReference;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.planner.SafeJsonPath;
import com.example.agent.skill.AgentSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskEvaluatorTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final TaskEvaluator evaluator = new TaskEvaluator(
            new SafeJsonPath(new ObjectMapper().findAndRegisterModules()));

    @Test
    void passesOnlyWhenSchemaSourceEntityAndCriterionAreSatisfied() {
        AgentCapabilityDefinition capability = capability("qq.artist.lookup");
        PlanTask task = task("artist", capability, Map.of("artistName",
                ValueExpression.literal(ValueType.STRING, "Mili")),
                new AcceptanceCriterion("artist-present", AcceptanceCriterion.Type.OUTPUT_PRESENT,
                        "$.artist", null, true, "必须返回歌手", Map.of()));
        TypedTaskResult result = TypedTaskResult.success(task.id(), capability.outputSchema(),
                Map.of("artistId", "artist:mili", "canonicalName", "Mili",
                        "profile", Map.of("name", "Mili"), "provider", "QQ_MUSIC"),
                "QQ_MUSIC", "artist:mili", List.of(new TypedEntityReference(GoalTargetType.ARTIST,
                        "Mili", "QQ_MUSIC", "artist:mili")), List.of("ev-artist"));

        TaskEvaluation evaluation = evaluator.evaluate(task, capability, Map.of("artistName", "Mili"), result);

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.PASS);
    }

    @Test
    void asksUserWhenRequiredResolvedInputIsAbsent() {
        AgentCapabilityDefinition capability = capability("qq.artist.lookup");
        PlanTask task = task("artist", capability, Map.of(), null);
        TypedTaskResult result = TypedTaskResult.success(task.id(), capability.outputSchema(),
                Map.of("artistId", "artist:mili", "canonicalName", "Mili",
                        "profile", Map.of(), "provider", "QQ_MUSIC"),
                "QQ_MUSIC", "artist:mili", List.of(new TypedEntityReference(GoalTargetType.ARTIST,
                        "Mili", "QQ_MUSIC", "artist:mili")), List.of("ev"));

        TaskEvaluation evaluation = evaluator.evaluate(task, capability, Map.of(), result);

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.ASK_USER);
        assertThat(evaluation.waitingSlot()).isEqualTo("artistName");
    }

    @Test
    void requestsReplanWhenInputAndReturnedEntityDisagree() {
        AgentCapabilityDefinition capability = capability("qq.artist.lookup");
        PlanTask task = task("artist", capability, Map.of("artistName",
                ValueExpression.literal(ValueType.STRING, "Mili")), null);
        TypedTaskResult result = TypedTaskResult.success(task.id(), capability.outputSchema(),
                Map.of("artistId", "artist:jay", "canonicalName", "周杰伦",
                        "profile", Map.of(), "provider", "QQ_MUSIC"),
                "QQ_MUSIC", "artist:jay", List.of(new TypedEntityReference(GoalTargetType.ARTIST,
                        "周杰伦", "QQ_MUSIC", "artist:jay")), List.of("ev"));

        TaskEvaluation evaluation = evaluator.evaluate(task, capability, Map.of("artistName", "Mili"), result);

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.REPLAN);
        assertThat(evaluation.findings()).extracting(EvaluationFinding::code)
                .contains("INPUT_OUTPUT_ENTITY_MISMATCH");
    }

    @Test
    void acceptsADeclaredEntityAliasButKeepsCanonicalIdentityEvidence() {
        AgentCapabilityDefinition capability = capability("qq.artist.lookup");
        PlanTask task = task("artist-alias", capability, Map.of("artistName",
                ValueExpression.literal(ValueType.STRING, "周杰伦")), null);
        TypedTaskResult result = TypedTaskResult.success(task.id(), capability.outputSchema(),
                Map.of("artistId", "artist:jay-chou", "canonicalName", "Jay Chou",
                        "profile", Map.of(), "provider", "QQ_MUSIC"),
                "QQ_MUSIC", "artist:jay-chou", List.of(new TypedEntityReference(
                        GoalTargetType.ARTIST, "Jay Chou", "QQ_MUSIC", "artist:jay-chou",
                        List.of("周杰伦", "Chou Chieh-lun"))), List.of("ev"));

        TaskEvaluation evaluation = evaluator.evaluate(task, capability,
                Map.of("artistName", "周杰伦"), result);

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.PASS);
    }

    @Test
    void rejectsSameNameArtistWhenProviderIdentityDoesNotMatch() {
        AgentCapabilityDefinition capability = capability("qq.artist.lookup");
        PlanTask task = task("same-name", capability, Map.of("artistName",
                ValueExpression.literal(ValueType.STRING, "Alex")), null);
        TypedTaskResult result = TypedTaskResult.success(task.id(), capability.outputSchema(),
                Map.of("artistId", "artist:alex-2", "canonicalName", "Alex",
                        "profile", Map.of(), "provider", "QQ_MUSIC"),
                "QQ_MUSIC", "artist:alex-2", List.of(new TypedEntityReference(
                        GoalTargetType.ARTIST, "Alex", "QQ_MUSIC", "artist:alex-2")), List.of("ev"));

        TaskEvaluation evaluation = evaluator.evaluate(task, capability,
                Map.of("artistName", "Alex", "artist",
                        Map.of("id", "artist:alex-1", "provider", "QQ_MUSIC")), result);

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.REPLAN);
        assertThat(evaluation.findings()).extracting(EvaluationFinding::subject)
                .contains("artist.id");
    }

    @Test
    void failsWhenMutationApiReturnsSuccessEnvelopeWithoutActualStateChange() {
        AgentCapabilityDefinition capability = capability("music.queue.add");
        PlanTask task = task("queue", capability, Map.of("tracks",
                        ValueExpression.literal(ValueType.ARRAY, List.of(Map.of("id", "track-1")))),
                new AcceptanceCriterion("changed", AcceptanceCriterion.Type.STATE_CHANGE,
                        "$.success", ValueExpression.literal(ValueType.BOOLEAN, true), true,
                        "队列必须真实变化", Map.of()));
        TypedTaskResult result = TypedTaskResult.success(task.id(), capability.outputSchema(),
                Map.of("success", false, "queuedCount", 0), "RUNTIME", "",
                List.of(), List.of("queue-event"));

        TaskEvaluation evaluation = evaluator.evaluate(task, capability,
                Map.of("tracks", List.of(Map.of("id", "track-1"))), result);

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.FAIL);
        assertThat(evaluation.findings()).extracting(EvaluationFinding::code)
                .contains("STATE_CHANGE_NOT_PROVEN");
    }

    @Test
    void revisesMalformedSuccessfulOutputInsteadOfCompletingIt() {
        AgentCapabilityDefinition capability = capability("music.track.search");
        PlanTask task = task("search", capability, Map.of("query",
                ValueExpression.literal(ValueType.STRING, "Mili")), null);
        TypedTaskResult result = TypedTaskResult.success(task.id(), capability.outputSchema(),
                Map.of("searchId", "s1", "tracks", "not-an-array", "provider", ""),
                "", "s1", List.of(), List.of("ev"));

        TaskEvaluation evaluation = evaluator.evaluate(task, capability, Map.of("query", "Mili"), result);

        assertThat(evaluation.decision()).isEqualTo(EvaluationDecision.REVISE);
        assertThat(evaluation.findings()).extracting(EvaluationFinding::code)
                .contains("OUTPUT_FIELD_TYPE_MISMATCH", "PROVIDER_MISSING", "OUTPUT_SOURCE_MISSING");
    }

    private AgentCapabilityDefinition capability(String id) {
        return registry.find(id).orElseThrow();
    }

    private static PlanTask task(String id, AgentCapabilityDefinition capability,
                                 Map<String, ValueExpression> inputs, AcceptanceCriterion criterion) {
        return new PlanTask(id, id, capability.id(), List.of("goal"), inputs, List.of(),
                criterion == null ? List.of() : List.of(criterion), 2);
    }
}
