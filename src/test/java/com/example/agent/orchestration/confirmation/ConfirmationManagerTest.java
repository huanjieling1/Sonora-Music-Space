package com.example.agent.orchestration.confirmation;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.skill.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmationManagerTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));

    @Test
    void appliesConfirmationRulesToMutationsButNotReadOnlyCapabilities() {
        ConfirmationManager manager = new ConfirmationManager();

        assertThat(List.of("music.playback.play", "music.queue.add", "music.track.favorite"))
                .allMatch(id -> manager.required(registry.find(id).orElseThrow()));
        assertThat(manager.required(registry.find("music.track.search").orElseThrow())).isFalse();
    }

    @Test
    void approvalIsPrincipalBoundAndValidOnlyForExactInputsAndIdempotencyKey() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        ConfirmationManager manager = new ConfirmationManager(Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(10));
        ConfirmationRequest pending = manager.create(UUID.randomUUID(), "user-1", "queue",
                registry.find("music.queue.add").orElseThrow(),
                Map.of("tracks", List.of(Map.of("id", "t1"))), "idem-1");
        ConfirmationRequest approved = manager.respond(pending, "user-1", true, now.plusSeconds(5));

        assertThat(approved.status()).isEqualTo(ConfirmationRequest.Status.APPROVED);
        assertThat(manager.authorized(approved,
                Map.of("tracks", List.of(Map.of("id", "t1"))), "idem-1")).isTrue();
        assertThat(manager.authorized(approved,
                Map.of("tracks", List.of(Map.of("id", "t2"))), "idem-1")).isFalse();
        assertThatThrownBy(() -> manager.respond(pending, "other-user", true, now.plusSeconds(5)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void expiredConfirmationCannotBeApprovedOrExecuted() {
        Instant now = Instant.parse("2026-08-18T12:00:00Z");
        ConfirmationManager manager = new ConfirmationManager(Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(1));
        ConfirmationRequest pending = manager.create(UUID.randomUUID(), "user-1", "favorite",
                registry.find("music.track.favorite").orElseThrow(),
                Map.of("track", Map.of("id", "t1"), "favorite", true), "idem-2");

        ConfirmationRequest expired = manager.respond(pending, "user-1", true, now.plusSeconds(61));

        assertThat(expired.status()).isEqualTo(ConfirmationRequest.Status.EXPIRED);
        assertThat(manager.authorized(expired, pending.pendingInputs(), "idem-2")).isFalse();
    }
}
