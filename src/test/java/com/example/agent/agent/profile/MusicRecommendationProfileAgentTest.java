package com.example.agent.agent.profile;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.UserTasteContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicRecommendationProfileAgentTest {
    @Test
    void returnsReadOnlyEvidenceSnapshotForTheCurrentUser() {
        UserTasteContext expected = new UserTasteContext("STABLE", "画像稳定", true,
                80, 31, 2_400_000, 0.72, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());
        var agent = new MusicRecommendationProfileAgent(userId -> expected);

        UserTasteContext result = agent.prepare(new MusicAgentTurn(7, UUID.randomUUID(), "推荐适合学习的歌"));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void profileReadFailureDegradesToEmptyContext() {
        var agent = new MusicRecommendationProfileAgent(userId -> {
            throw new IllegalStateException("offline");
        });

        UserTasteContext result = agent.prepare(new MusicAgentTurn(7, UUID.randomUUID(), "推荐一些歌"));

        assertThat(result.hasEvidence()).isFalse();
        assertThat(result.stage()).isEqualTo("EMPTY");
    }
}
