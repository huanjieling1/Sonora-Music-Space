package com.example.agent.agent.profile;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.UserTasteContext;
import org.springframework.stereotype.Component;

import java.util.List;

/** Supplies a read-only, evidence-carrying profile snapshot to recommendation workflows. */
@Component
public class MusicRecommendationProfileAgent {
    private final MusicProfileContextReader contextReader;

    public MusicRecommendationProfileAgent(MusicProfileContextReader contextReader) {
        this.contextReader = contextReader;
    }

    public UserTasteContext prepare(MusicAgentTurn turn) {
        try {
            UserTasteContext context = contextReader.read(turn.userId());
            return context == null ? empty() : context;
        } catch (RuntimeException ignored) {
            return empty();
        }
    }

    private static UserTasteContext empty() {
        return new UserTasteContext("EMPTY", "暂无画像", false,
                0, 0, 0, 0, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of("画像读取失败或暂无可靠证据。"));
    }
}
