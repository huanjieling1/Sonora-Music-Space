package com.example.agent.agent.profile;

import com.example.agent.agent.contract.MusicChildAgentDescriptor;
import com.example.agent.agent.contract.MusicTaskEvidence;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.contract.MusicTaskResult;
import com.example.agent.agent.main.MusicWorkflowChildAgent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Capability adapter for both narrative profile analysis and read-only recommendation context. */
@Component
public final class MusicProfileWorkflowChildAgent implements MusicWorkflowChildAgent {
    public static final String PURPOSE = "purpose";
    public static final String ANALYSIS = "analysis";
    public static final String RECOMMENDATION = "recommendation";

    private static final MusicChildAgentDescriptor DESCRIPTOR = new MusicChildAgentDescriptor(
            "music-profile-agent", "Profile Agent", Set.of("music-profile-insight"), 100);

    private final MusicProfileAgent profileAgent;
    private final MusicRecommendationProfileAgent recommendationProfileAgent;

    public MusicProfileWorkflowChildAgent(MusicProfileAgent profileAgent,
                                          MusicRecommendationProfileAgent recommendationProfileAgent) {
        this.profileAgent = profileAgent;
        this.recommendationProfileAgent = recommendationProfileAgent;
    }

    @Override
    public MusicChildAgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public MusicTaskResult execute(MusicTaskInvocation invocation) {
        String purpose = String.valueOf(invocation.inputs().getOrDefault(PURPOSE, RECOMMENDATION));
        Object payload = ANALYSIS.equals(purpose)
                ? profileAgent.analyze(invocation.turn())
                : recommendationProfileAgent.prepare(invocation.turn());
        boolean successful = payload != null;
        String evidenceType = ANALYSIS.equals(purpose) ? "PROFILE_NARRATIVE" : "PROFILE_CONTEXT";
        return new MusicTaskResult(invocation.task().id(), successful, payload,
                successful ? List.of(new MusicTaskEvidence(evidenceType, "music-profile", "",
                        Map.of("purpose", purpose))) : List.of(),
                successful ? "画像证据已准备" : "画像 Agent 没有返回结果",
                successful ? "" : "EMPTY_PROFILE_RESULT");
    }
}
