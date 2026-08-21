package com.example.agent.agent.response;

import java.util.List;

/** One response section, kept in the same order as UserGoalGraph.goals. */
public record GoalResponseSection(
        String goalId,
        String title,
        GoalResponseStatus status,
        List<GroundedResponseFact> facts,
        String message
) {
    public GoalResponseSection {
        if (goalId == null || goalId.isBlank()) throw new IllegalArgumentException("响应段必须关联目标");
        goalId = goalId.strip();
        title = title == null || title.isBlank() ? goalId : title.strip();
        status = status == null ? GoalResponseStatus.FAILED : status;
        facts = facts == null ? List.of() : List.copyOf(facts);
        message = message == null ? "" : message.strip();
    }
}
