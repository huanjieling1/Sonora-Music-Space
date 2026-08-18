package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicSupportContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicSupportContextAgentTest {
    @Test
    void detectsOrdinarySadnessAsTransientSupportNeed() {
        MusicSupportContextAgent agent = new MusicSupportContextAgent(request -> Optional.empty());

        MusicSupportContext context = agent.analyze(turn("我现在不开心"));

        assertThat(context.actionable()).isTrue();
        assertThat(context.signal()).isEqualTo(MusicSupportContext.EmotionalSignal.SADNESS);
        assertThat(context.goal()).isEqualTo(MusicSupportContext.SupportGoal.SOOTHE);
        assertThat(context.safetyConcern()).isFalse();
    }

    @Test
    void detectsMildHappinessAsCelebrationContext() {
        MusicSupportContextAgent agent = new MusicSupportContextAgent(request -> Optional.empty());

        MusicSupportContext context = agent.analyze(turn("我有点开心"));

        assertThat(context.actionable()).isTrue();
        assertThat(context.signal()).isEqualTo(MusicSupportContext.EmotionalSignal.CELEBRATION);
        assertThat(context.goal()).isEqualTo(MusicSupportContext.SupportGoal.ENERGIZE);
    }

    @Test
    void deterministicSafetySignalCannotBeDowngradedByModel() {
        MusicSupportContextAgent agent = new MusicSupportContextAgent(request -> Optional.of(
                new MusicSupportContext(MusicSupportContext.InteractionType.CASUAL_CONVERSATION,
                        MusicSupportContext.EmotionalSignal.NONE, MusicSupportContext.SupportGoal.NONE,
                        0.99, "")));

        MusicSupportContext context = agent.analyze(turn("我不想活下去了"));

        assertThat(context.safetyConcern()).isTrue();
        assertThat(context.goal()).isEqualTo(MusicSupportContext.SupportGoal.SAFETY);
    }

    @Test
    void ordinaryNeutralConversationDoesNotTriggerMusicAction() {
        MusicSupportContextAgent agent = new MusicSupportContextAgent(request -> Optional.empty());

        assertThat(agent.analyze(turn("你觉得音乐是什么")).actionable()).isFalse();
    }

    private static MusicAgentTurn turn(String request) {
        return new MusicAgentTurn(1, UUID.randomUUID(), request);
    }
}
