package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicProactiveSuggestion;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.skill.AgentSkillDefinition;

import java.util.List;

/** Extension point that turns one runtime Skill into a bounded support action. */
public interface MusicSupportCapabilityAdapter {
    boolean supports(AgentSkillDefinition skill);

    MusicAgentRoute executionRoute();

    String executionRequest(MusicSupportContext context);

    List<MusicProactiveSuggestion> followUps(MusicSupportContext context, AgentSkillDefinition skill);

    default int scoreBonus(MusicSupportContext context) {
        return 0;
    }
}
