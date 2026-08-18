package com.example.agent.agent.capability;

import com.example.agent.skill.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

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
}
