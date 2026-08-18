package com.example.agent.skill;

import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.agent.contract.MusicSupportContext;

import java.util.Set;

/** Optional runtime metadata describing when a Skill can proactively help. */
public record AgentSkillSupportAffordance(
        boolean proactive,
        Set<MusicSupportContext.EmotionalSignal> contexts,
        Set<MusicSupportContext.SupportGoal> goals,
        MusicAutonomyLevel autonomy,
        String outputAction,
        int weight
) {
    public AgentSkillSupportAffordance {
        contexts = contexts == null ? Set.of() : Set.copyOf(contexts);
        goals = goals == null ? Set.of() : Set.copyOf(goals);
        autonomy = autonomy == null ? MusicAutonomyLevel.DISABLED : autonomy;
        outputAction = outputAction == null ? "" : outputAction.strip();
        weight = Math.max(0, Math.min(100, weight));
        if (!proactive) {
            contexts = Set.of();
            goals = Set.of();
            autonomy = MusicAutonomyLevel.DISABLED;
            outputAction = "";
            weight = 0;
        }
    }

    public static AgentSkillSupportAffordance disabled() {
        return new AgentSkillSupportAffordance(false, Set.of(), Set.of(),
                MusicAutonomyLevel.DISABLED, "", 0);
    }

    public boolean supports(MusicSupportContext context) {
        return proactive && context != null && contexts.contains(context.signal()) && goals.contains(context.goal());
    }
}
