package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySchema;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.TypedEntityReference;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import com.example.agent.agent.goal.DeterministicMusicGoalParser;
import com.example.agent.skill.AgentSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskResultStoreResolverTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));
    private final DeterministicMusicGoalParser parser = new DeterministicMusicGoalParser();
    private final GenericPlanSynthesizer synthesizer = new GenericPlanSynthesizer(registry);
    private final PlanCompiler compiler = new PlanCompiler(new PlanValidator(registry));
    private final ValueExpressionResolver resolver = new ValueExpressionResolver(
            new SafeJsonPath(objectMapper), new ProfileFieldAccessPolicy());

    @Test
    void storesSchemaCarryingResultAndResolvesDeclaredTaskOutputJsonPath() {
        CompiledPlan plan = compoundPlan();
        TaskResultStore store = new TaskResultStore(plan, registry);
        TypedTaskResult search = trackSearchResult("task-3-execute");
        store.store(search);

        ReferenceResolution title = resolver.resolve(
                ValueExpression.taskOutput(ValueType.STRING, "task-3-execute", "$.tracks[0]['title']"),
                context("task-4-execute", store));
        ReferenceResolution tracks = resolver.resolve(
                ValueExpression.taskOutput(ValueType.ARRAY, "task-3-execute", "$.tracks"),
                context("task-4-execute", store));

        assertThat(title.resolved()).isTrue();
        assertThat(title.value()).isEqualTo("world.execute(me);");
        assertThat(title.valueType()).isEqualTo(ValueType.STRING);
        assertThat(tracks.resolved()).isTrue();
        assertThat(tracks.value()).asList().hasSize(1);
        assertThat(search.outputSchema().id()).isEqualTo("music.track.search.output.v1");
        assertThat(search.provider()).isEqualTo("QQ_MUSIC");
        assertThat(search.resourceId()).isEqualTo("search-42");
        assertThat(search.entities()).singleElement().satisfies(entity -> {
            assertThat(entity.canonicalName()).isEqualTo("world.execute(me);");
            assertThat(entity.entityId()).isEqualTo("track-42");
        });
        assertThat(search.evidenceIds()).containsExactly("evidence-search-42");
    }

    @Test
    void typedResultRoundTripsAndDefensivelyCopiesJsonOutput() throws Exception {
        java.util.LinkedHashMap<String, Object> mutable = new java.util.LinkedHashMap<>();
        mutable.put("searchId", "search-42");
        mutable.put("tracks", List.of(Map.of("id", "track-42")));
        mutable.put("provider", "QQ_MUSIC");
        TypedTaskResult result = TypedTaskResult.success("task-3-execute",
                capability("music.track.search").outputSchema(), mutable, "QQ_MUSIC", "search-42",
                List.of(new TypedEntityReference(GoalTargetType.TRACK, "world.execute(me);",
                        "QQ_MUSIC", "track-42")), List.of("evidence-search-42"));
        mutable.put("unexpected", true);

        String json = objectMapper.writeValueAsString(result);
        TypedTaskResult restored = objectMapper.readValue(json, TypedTaskResult.class);

        assertThat(restored).isEqualTo(result);
        assertThat(((Map<?, ?>) result.output()).containsKey("unexpected")).isFalse();
        assertThatThrownBy(() -> ((Map<Object, Object>) result.output()).put("bad", true))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(json).contains("outputSchema", "provider", "resourceId", "canonicalName",
                "entityId", "evidenceIds");
    }

    @Test
    void returnsStructuredErrorsForMissingFailedAndInvalidReferences() {
        CompiledPlan plan = compoundPlan();
        TaskResultStore missingStore = new TaskResultStore(plan, registry);
        assertError(resolver.resolve(ValueExpression.taskOutput(ValueType.ARRAY,
                "task-3-execute", "$.tracks"), context("task-4-execute", missingStore)),
                "TASK_RESULT_NOT_AVAILABLE");

        TaskResultStore failedStore = new TaskResultStore(plan, registry);
        failedStore.store(TypedTaskResult.failure("task-3-execute",
                capability("music.track.search").outputSchema(), "PROVIDER_TIMEOUT",
                "上游超时", List.of("timeout-log")));
        assertError(resolver.resolve(ValueExpression.taskOutput(ValueType.ARRAY,
                "task-3-execute", "$.tracks"), context("task-4-execute", failedStore)),
                "UPSTREAM_TASK_FAILED");

        TaskResultStore pathStore = new TaskResultStore(plan, registry);
        pathStore.store(trackSearchResult("task-3-execute"));
        assertError(resolver.resolve(ValueExpression.taskOutput(ValueType.STRING,
                "task-3-execute", "$.tracks[0].missing"), context("task-4-execute", pathStore)),
                "JSON_PATH_NOT_FOUND");
        assertError(resolver.resolve(ValueExpression.taskOutput(ValueType.STRING,
                "task-3-execute", "tracks[0]"), context("task-4-execute", pathStore)),
                "INVALID_JSON_PATH");
        assertError(resolver.resolve(ValueExpression.taskOutput(ValueType.INTEGER,
                "task-3-execute", "$.tracks[0].title"), context("task-4-execute", pathStore)),
                "REFERENCE_TYPE_MISMATCH");
    }

    @Test
    void preventsParallelTaskFromReadingUndeclaredUpstreamData() {
        UserGoalGraph graph = parser.parse("搜索周杰伦的资料，同时搜索林俊杰的资料");
        CompiledPlan plan = compiler.compile(graph, synthesizer.synthesize(graph),
                PlanValidationContext.standard("user-1"));
        TaskResultStore store = new TaskResultStore(plan, registry);
        store.store(artistLookupResult("task-1-execute", "周杰伦", "artist-1"));

        ReferenceResolution resolution = resolver.resolve(
                ValueExpression.taskOutput(ValueType.STRING, "task-1-execute", "$.canonicalName"),
                context("task-2-execute", store));

        assertError(resolution, "UNDECLARED_UPSTREAM_ACCESS");
    }

    @Test
    void validatesResultSchemaAndRequiredEvidenceMetadataBeforeStorage() {
        TaskResultStore store = new TaskResultStore(compoundPlan(), registry);
        CapabilitySchema wrongSchema = capability("qq.artist.lookup").outputSchema();
        assertThatThrownBy(() -> store.store(TypedTaskResult.success("task-3-execute", wrongSchema,
                Map.of("artistId", "x", "canonicalName", "x", "profile", Map.of(),
                        "provider", "QQ_MUSIC"), "QQ_MUSIC", "x",
                List.of(new TypedEntityReference(GoalTargetType.ARTIST, "x", "QQ_MUSIC", "x")),
                List.of("evidence"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Schema");

        CapabilitySchema trackSchema = capability("music.track.search").outputSchema();
        assertThatThrownBy(() -> store.store(TypedTaskResult.success("task-3-execute", trackSchema,
                Map.of("searchId", "s", "tracks", List.of(), "provider", "QQ_MUSIC"),
                "", "s", List.of(), List.of("evidence"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provider");
        assertThatThrownBy(() -> TypedTaskResult.success("task-3-execute", trackSchema,
                Map.of("searchId", "s", "tracks", List.of(), "provider", "QQ_MUSIC"),
                "QQ_MUSIC", "s", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("证据 ID");
    }

    @Test
    void resolvesOwnedProfileAndProtectsSensitiveFields() {
        Map<String, Object> profileRoot = Map.of("musicProfile", Map.of(
                "summary", Map.of("stage", "MATURE"),
                "explicitPreferences", List.of(Map.of("artist", "Mili"))));
        ReferenceResolutionContext normal = profileContext(profileRoot, "user-1", "user-1", Set.of());

        ReferenceResolution stage = resolver.resolve(
                ValueExpression.profileValue(ValueType.STRING, "$.musicProfile.summary.stage"), normal);
        assertThat(stage.resolved()).isTrue();
        assertThat(stage.value()).isEqualTo("MATURE");

        ValueExpression.ProfileValue sensitive = ValueExpression.profileValue(ValueType.STRING,
                "$.musicProfile.explicitPreferences[0].artist");
        assertError(resolver.resolve(sensitive, normal), "SENSITIVE_PROFILE_FIELD_DENIED");

        ReferenceResolutionContext granted = profileContext(profileRoot, "user-1", "user-1",
                Set.of("$.musicProfile.explicitPreferences"));
        assertThat(resolver.resolve(sensitive, granted).value()).isEqualTo("Mili");

        ReferenceResolutionContext otherUser = profileContext(profileRoot, "user-2", "user-1", Set.of(
                "$.musicProfile.explicitPreferences"));
        assertError(resolver.resolve(ValueExpression.profileValue(ValueType.STRING,
                "$.musicProfile.summary.stage"), otherUser), "PROFILE_OWNER_MISMATCH");
    }

    private CompiledPlan compoundPlan() {
        UserGoalGraph graph = parser.parse("找出我最喜欢的歌手资料，再推荐三首他的歌并加入队列");
        return compiler.compile(graph, synthesizer.synthesize(graph),
                PlanValidationContext.standard("user-1"));
    }

    private TypedTaskResult trackSearchResult(String taskId) {
        return TypedTaskResult.success(taskId, capability("music.track.search").outputSchema(),
                Map.of("searchId", "search-42", "tracks", List.of(Map.of(
                                "id", "track-42", "title", "world.execute(me);", "artist", "Mili")),
                        "provider", "QQ_MUSIC"),
                "QQ_MUSIC", "search-42", List.of(new TypedEntityReference(GoalTargetType.TRACK,
                        "world.execute(me);", "QQ_MUSIC", "track-42")),
                List.of("evidence-search-42"));
    }

    private TypedTaskResult artistLookupResult(String taskId, String name, String id) {
        return TypedTaskResult.success(taskId, capability("qq.artist.lookup").outputSchema(),
                Map.of("artistId", id, "canonicalName", name, "profile", Map.of("name", name),
                        "provider", "QQ_MUSIC"),
                "QQ_MUSIC", id, List.of(new TypedEntityReference(GoalTargetType.ARTIST,
                        name, "QQ_MUSIC", id)), List.of("evidence-" + id));
    }

    private AgentCapabilityDefinition capability(String id) {
        return registry.find(id).orElseThrow();
    }

    private static ReferenceResolutionContext context(String consumerTaskId, TaskResultStore store) {
        return new ReferenceResolutionContext(consumerTaskId, "user-1", "user-1",
                Map.of("musicProfile", Map.of()), Map.of(), Set.of(), store);
    }

    private static ReferenceResolutionContext profileContext(Object profileRoot, String principal,
                                                               String owner, Set<String> allowed) {
        return new ReferenceResolutionContext("task-1-execute", principal, owner,
                profileRoot, Map.of(), allowed, null);
    }

    private static void assertError(ReferenceResolution resolution, String code) {
        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.error()).isNotNull();
        assertThat(resolution.error().code()).isEqualTo(code);
    }
}
