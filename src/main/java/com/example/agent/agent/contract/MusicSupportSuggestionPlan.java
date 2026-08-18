package com.example.agent.agent.contract;

import com.example.agent.model.bo.AgentActionType;

import java.util.List;

/** Selected runtime Skill, bounded execution request and user-visible alternatives for one support turn. */
public record MusicSupportSuggestionPlan(
        String skillId,
        String skillName,
        MusicAgentRoute executionRoute,
        String executionRequest,
        MusicAutonomyLevel autonomy,
        AgentActionType expectedEvidence,
        List<MusicProactiveSuggestion> followUps
) {
    public MusicSupportSuggestionPlan {
        skillId = skillId == null ? "" : skillId.strip();
        skillName = skillName == null ? "" : skillName.strip();
        executionRequest = executionRequest == null ? "" : executionRequest.strip();
        autonomy = autonomy == null ? MusicAutonomyLevel.DISABLED : autonomy;
        followUps = followUps == null ? List.of() : List.copyOf(followUps);
        if (skillId.isBlank() || skillName.isBlank() || executionRoute == null
                || executionRequest.isBlank() || expectedEvidence == null) {
            throw new IllegalArgumentException("主动建议计划缺少可执行能力或验收合同");
        }
    }
}
