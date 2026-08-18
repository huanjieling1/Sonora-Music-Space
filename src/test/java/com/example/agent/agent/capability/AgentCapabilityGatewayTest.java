package com.example.agent.agent.capability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentCapabilityGatewayTest {
    private final AgentCapabilityGateway gateway = new AgentCapabilityGateway();

    @Test
    void routesCapabilityQuestionsWithoutUsingGeneralConversation() {
        assertThat(gateway.classify("你有哪些能力").type()).isEqualTo(AgentScopeType.CAPABILITY_INQUIRY);
        assertThat(gateway.classify("Sonora 支持什么功能？").type()).isEqualTo(AgentScopeType.CAPABILITY_INQUIRY);
    }

    @Test
    void keepsMusicAndRecommendationFollowUpsInsideTheDomain() {
        assertThat(gateway.classify("推荐适合夜晚的歌").type()).isEqualTo(AgentScopeType.MUSIC);
        assertThat(gateway.classify("这些我不喜欢，换一批").type()).isEqualTo(AgentScopeType.MUSIC);
        assertThat(gateway.classify("播放第二首").type()).isEqualTo(AgentScopeType.MUSIC);
    }

    @Test
    void rejectsKnownUnsupportedDomainsAndClarifiesUnknownOnes() {
        assertThat(gateway.classify("帮我查一下明天天气").type()).isEqualTo(AgentScopeType.OUT_OF_SCOPE);
        assertThat(gateway.classify("帮我写一段 Java 代码").type()).isEqualTo(AgentScopeType.OUT_OF_SCOPE);
        assertThat(gateway.classify("秦始皇是谁").type()).isEqualTo(AgentScopeType.NEEDS_CLARIFICATION);
    }

    @Test
    void musicSubjectOverridesWordsThatAreOtherwiseOutOfScope() {
        assertThat(gateway.classify("推荐适合写代码时听的音乐").type()).isEqualTo(AgentScopeType.MUSIC);
        assertThat(gateway.classify("找一些电影配乐").type()).isEqualTo(AgentScopeType.MUSIC);
    }
}
