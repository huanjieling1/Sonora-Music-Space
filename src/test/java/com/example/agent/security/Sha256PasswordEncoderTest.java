package com.example.agent.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256PasswordEncoderTest {
    private final Sha256PasswordEncoder encoder = new Sha256PasswordEncoder(10_000);

    @Test
    void encodesWithRandomSaltAndMatchesOriginalPassword() {
        String first = encoder.encode("AgentPass123");
        String second = encoder.encode("AgentPass123");

        assertThat(first).startsWith("sha256$10000$").isNotEqualTo(second);
        assertThat(first).doesNotContain("AgentPass123");
        assertThat(encoder.matches("AgentPass123", first)).isTrue();
        assertThat(encoder.matches("wrong-password", first)).isFalse();
    }

    @Test
    void rejectsMalformedStoredValues() {
        assertThat(encoder.matches("password", "not-a-valid-hash")).isFalse();
        assertThat(encoder.matches("password", null)).isFalse();
    }
}
