package com.example.agent.agent.response;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.MusicTrackBo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResponseGuardTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry();
    private final AgentResponseGuard guard = new AgentResponseGuard(registry);

    @Test
    void replacesGenericAiCapabilityHallucination() {
        String hallucination = "作为一个人工智能助手，我具备多种能力，可以协助用户完成各种任务。"
                + "我可以进行语言理解、知识问答和娱乐互动，还可以设置提醒、提供天气预报并帮助用户编程。";

        String answer = guard.enforce(MusicAgentRoute.CONVERSATION, hallucination, List.of());

        assertThat(answer).isEqualTo(registry.capabilityAnswer());
        assertThat(answer).doesNotContain("我可以提供天气", "帮助你编程");
    }

    @Test
    void blocksPlaybackSuccessWithoutStructuredActionEvidence() {
        String answer = guard.enforce(MusicAgentRoute.CONVERSATION, "已经开始播放《晴天》。", List.of());

        assertThat(answer).isEqualTo(registry.unverifiedActionAnswer());
    }

    @Test
    void allowsPlaybackClaimWhenPlayActionExists() {
        MusicTrackBo track = new MusicTrackBo("1", "晴天", List.of("周杰伦"), "叶惠美", "", 1000,
                "", "qq", "stream", "/play/1", "");

        String answer = guard.enforce(MusicAgentRoute.RESULT_PLAYBACK, "已经开始播放《晴天》。",
                List.of(AgentActionBo.playTrack(track)));

        assertThat(answer).isEqualTo("已经开始播放《晴天》。");
    }

    @Test
    void capabilityRouteAlwaysUsesRegistryInsteadOfModelText() {
        assertThat(guard.enforce(MusicAgentRoute.CAPABILITY_INQUIRY, "我什么都会", List.of()))
                .isEqualTo(registry.capabilityAnswer());
    }
}
