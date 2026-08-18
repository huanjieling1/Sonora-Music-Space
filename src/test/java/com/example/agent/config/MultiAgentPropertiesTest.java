package com.example.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAgentPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsIndependentRoleSettings() {
        contextRunner.withPropertyValues(
                        "agent.multi-agent.enabled=true",
                        "agent.multi-agent.profile.model-name=profile-model",
                        "agent.multi-agent.profile.temperature=0.65",
                        "agent.multi-agent.profile.max-tokens=1200",
                        "agent.multi-agent.intent.model-name=intent-model",
                        "agent.multi-agent.intent.temperature=0.0",
                        "agent.multi-agent.intent.max-tokens=600")
                .run(context -> {
                    MultiAgentProperties properties = context.getBean(MultiAgentProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.profile().modelName()).isEqualTo("profile-model");
                    assertThat(properties.profile().temperature()).isEqualTo(0.65);
                    assertThat(properties.profile().maxTokens()).isEqualTo(1200);
                    assertThat(properties.intent().modelName()).isEqualTo("intent-model");
                    assertThat(properties.intent().temperature()).isZero();
                    assertThat(properties.intent().maxTokens()).isEqualTo(600);
                    assertThat(properties.conversation().temperature()).isEqualTo(0.2);
                });
    }

    @Test
    void clampsUnsafeRoleSettings() {
        contextRunner.withPropertyValues(
                        "agent.multi-agent.profile.temperature=4.5",
                        "agent.multi-agent.profile.max-tokens=20000")
                .run(context -> {
                    MultiAgentProperties properties = context.getBean(MultiAgentProperties.class);
                    assertThat(properties.profile().temperature()).isEqualTo(1.0);
                    assertThat(properties.profile().maxTokens()).isEqualTo(8192);
                });
    }

    @EnableConfigurationProperties(MultiAgentProperties.class)
    static class TestConfiguration {
    }
}
