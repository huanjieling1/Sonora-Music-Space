package com.example.agent.agent.capability;

import com.example.agent.skill.AgentSkillRegistry;
import com.example.agent.agent.contract.planning.ValueType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentCapabilityRegistryTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry();

    @Test
    void capabilityAnswerIsRenderedFromLoadedSkills() {
        assertThat(registry.capabilityAnswer())
                .contains("音乐发现", "QQ 音乐公开歌单发现", "音乐播放控制", "音乐画像分析")
                .doesNotContain("天气、提醒、编程、新闻");
        assertThat(registry.matches("给我推荐一些歌曲")).isTrue();
        assertThat(registry.supportsTool("recommendMusic")).isTrue();
        assertThat(registry.supportsTool("getWeather")).isFalse();
    }

    @Test
    void contributedCapabilityAutomaticallyJoinsSelfDescriptionAndMatching() {
        AgentCapabilityContributor weather = () -> java.util.List.of(new AgentCapabilityDefinition(
                "weather", "天气查询", "查询实时天气", java.util.Set.of(),
                java.util.Set.of("天气", "气温"), "module:weather"));
        AgentCapabilityRegistry extended = new AgentCapabilityRegistry(
                new AgentSkillRegistry(), java.util.List.of(weather));

        assertThat(extended.capabilityAnswer()).contains("天气查询");
        assertThat(extended.matches("明天的天气怎么样")).isTrue();
    }

    @Test
    void onlyExecutionRoleCanInvokeCatalogTools() {
        AgentToolAuthorizer authorizer = new AgentToolAuthorizer(registry);

        authorizer.requireAllowed(AgentRole.EXECUTION, "recommendMusic");
        assertThatThrownBy(() -> authorizer.requireAllowed(AgentRole.CONVERSATION, "recommendMusic"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> authorizer.requireAllowed(AgentRole.EXECUTION, "getWeather"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void exposesOnlyStronglyTypedContractsToTheGenericPlanner() throws Exception {
        AgentCapabilityRegistry typed = new AgentCapabilityRegistry(new AgentSkillRegistry(),
                List.of(new MusicPlanningCapabilityContributor()));

        assertThat(typed.planningCapabilities()).hasSize(11);
        assertThat(typed.planningCapabilities()).extracting(AgentCapabilityDefinition::id)
                .contains("profile.music.read", "profile.artist.resolve", "music.track.search",
                        "qq.artist.lookup", "qq.playlist.search", "qq.chart.read",
                        "music.playback.play", "music.queue.add", "music.track.favorite",
                        "music.recommendation.feedback",
                        "planner.goal.accept");

        assertThat(typed.find("music.track.search").orElseThrow().supportedOperations())
                .containsExactlyInAnyOrder(com.example.agent.agent.contract.planning.GoalOperation.SEARCH,
                        com.example.agent.agent.contract.planning.GoalOperation.RECOMMEND);

        AgentCapabilityDefinition artist = typed.find("qq.artist.lookup").orElseThrow();
        assertThat(artist.inputSchema().fields().get("artistName").required()).isTrue();
        assertThat(artist.outputSchema().fields()).containsKeys("artistId", "canonicalName", "profile");
        assertThat(artist.sideEffect()).isEqualTo(CapabilitySideEffect.READ_ONLY);
        assertThat(artist.evidencePolicy().entityMatchRequired()).isTrue();

        AgentCapabilityDefinition queue = typed.find("music.queue.add").orElseThrow();
        assertThat(queue.sideEffect()).isEqualTo(CapabilitySideEffect.REVERSIBLE_SESSION);
        assertThat(queue.confirmationPolicy()).isEqualTo(CapabilityConfirmationPolicy.EXPLICIT_INTENT);
        assertThat(queue.executionPolicy().maxAttempts()).isEqualTo(1);

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(artist);
        AgentCapabilityDefinition restored = new ObjectMapper().findAndRegisterModules()
                .readValue(json, AgentCapabilityDefinition.class);
        assertThat(restored).isEqualTo(artist);
    }

    @Test
    void rejectsPlannerCapabilityWithMissingOutputContract() {
        AgentCapabilityContributor invalid = () -> List.of(capability("invalid.missing-output",
                CapabilitySchema.empty("invalid.input.v1"), CapabilitySchema.empty("invalid.output.v1"),
                List.of(), CapabilityEvidencePolicy.read("RESULT", false, false)));

        assertThatThrownBy(() -> new AgentCapabilityRegistry(new AgentSkillRegistry(), List.of(invalid)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one output field");
    }

    @Test
    void rejectsConflictingSchemasAndDuplicatePreconditions() {
        CapabilitySchema first = CapabilitySchema.object("shared.input.v1",
                Map.of("query", CapabilityFieldSchema.required(ValueType.STRING, "query")));
        CapabilitySchema second = CapabilitySchema.object("shared.input.v1",
                Map.of("artist", CapabilityFieldSchema.required(ValueType.STRING, "artist")));
        CapabilitySchema output = CapabilitySchema.object("shared.output.v1",
                Map.of("success", CapabilityFieldSchema.required(ValueType.BOOLEAN, "success")));
        AgentCapabilityContributor conflict = () -> List.of(
                capability("schema.first", first, output, List.of(),
                        CapabilityEvidencePolicy.read("RESULT", false, false)),
                capability("schema.second", second, output, List.of(),
                        CapabilityEvidencePolicy.read("RESULT", false, false)));

        assertThatThrownBy(() -> new AgentCapabilityRegistry(new AgentSkillRegistry(), List.of(conflict)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Schema conflict");

        CapabilityPrecondition repeated = new CapabilityPrecondition("same",
                CapabilityPrecondition.Type.CUSTOM, true, "same");
        AgentCapabilityContributor duplicate = () -> List.of(capability("duplicate.preconditions",
                CapabilitySchema.empty("duplicate.input.v1"), output,
                List.of(repeated, repeated), CapabilityEvidencePolicy.read("RESULT", false, false)));
        assertThatThrownBy(() -> new AgentCapabilityRegistry(new AgentSkillRegistry(), List.of(duplicate)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("duplicate precondition");
    }

    @Test
    void rejectsUnknownToolAndMissingEvidencePolicy() {
        CapabilitySchema output = CapabilitySchema.object("validation.output.v1",
                Map.of("success", CapabilityFieldSchema.required(ValueType.BOOLEAN, "success")));
        AgentCapabilityDefinition unknownTool = new AgentCapabilityDefinition(
                "unknown.tool", "Unknown", "Unknown tool", Set.of("notRegistered"), Set.of(), "test", true,
                CapabilitySchema.empty("unknown.input.v1"), output, List.of(), CapabilitySideEffect.READ_ONLY,
                CapabilityConfirmationPolicy.NEVER, CapabilityExecutionPolicy.readOnly(1, 0, 1),
                CapabilityEvidencePolicy.read("RESULT", false, false));
        assertThatThrownBy(() -> new AgentCapabilityRegistry(new AgentSkillRegistry(),
                List.of(() -> List.of(unknownTool))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not actually registered");

        AgentCapabilityContributor missingEvidence = () -> List.of(capability("missing.evidence",
                CapabilitySchema.empty("evidence.input.v1"), output, List.of(),
                CapabilityEvidencePolicy.none()));
        assertThatThrownBy(() -> new AgentCapabilityRegistry(new AgentSkillRegistry(),
                List.of(missingEvidence)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("verifiable evidence");
    }

    private static AgentCapabilityDefinition capability(String id, CapabilitySchema input,
                                                          CapabilitySchema output,
                                                          List<CapabilityPrecondition> preconditions,
                                                          CapabilityEvidencePolicy evidence) {
        return new AgentCapabilityDefinition(id, id, "test capability", Set.of(), Set.of(), "test", true,
                input, output, preconditions, CapabilitySideEffect.READ_ONLY,
                CapabilityConfirmationPolicy.NEVER, CapabilityExecutionPolicy.readOnly(1, 0, 1), evidence);
    }
}
