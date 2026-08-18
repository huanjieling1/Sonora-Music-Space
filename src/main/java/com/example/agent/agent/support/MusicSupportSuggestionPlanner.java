package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.agent.contract.MusicProactiveSuggestion;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.skill.AgentSkillDefinition;
import com.example.agent.skill.AgentSkillRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Selects only currently loaded Skills that explicitly opt in to the current support context. */
@Component
public class MusicSupportSuggestionPlanner {
    private final AgentSkillRegistry registry;
    private final List<MusicSupportCapabilityAdapter> adapters;

    public MusicSupportSuggestionPlanner() {
        this(new AgentSkillRegistry(), List.of(new MusicDiscoverySupportAdapter(),
                new PublicPlaylistSupportAdapter()));
    }

    @Autowired
    public MusicSupportSuggestionPlanner(AgentSkillRegistry registry,
                                         List<MusicSupportCapabilityAdapter> adapters) {
        this.registry = registry;
        this.adapters = adapters == null ? List.of() : List.copyOf(adapters);
    }

    public Optional<MusicSupportSuggestionPlan> plan(MusicSupportContext context) {
        if (context == null || !context.actionable()) return Optional.empty();
        List<Candidate> candidates = new ArrayList<>();
        for (AgentSkillDefinition skill : registry.skills()) {
            if (!skill.supportAffordance().supports(context)) continue;
            adapters.stream().filter(adapter -> adapter.supports(skill)).findFirst()
                    .ifPresent(adapter -> candidates.add(new Candidate(skill, adapter,
                            skill.supportAffordance().weight() * 1000 + skill.priority()
                                    + adapter.scoreBonus(context))));
        }
        if (candidates.isEmpty()) return Optional.empty();
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed());
        Candidate selected = candidates.get(0);
        List<MusicProactiveSuggestion> followUps = candidates.stream()
                .flatMap(candidate -> candidate.adapter().followUps(context, candidate.skill()).stream())
                .distinct().limit(3).toList();
        AgentActionType expected;
        try {
            expected = AgentActionType.valueOf(selected.skill().supportAffordance().outputAction());
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        MusicAutonomyLevel autonomy = selected.skill().supportAffordance().autonomy();
        return Optional.of(new MusicSupportSuggestionPlan(selected.skill().id(), selected.skill().name(),
                selected.adapter().executionRoute(), selected.adapter().executionRequest(context),
                autonomy, expected, followUps));
    }

    private record Candidate(AgentSkillDefinition skill, MusicSupportCapabilityAdapter adapter, int score) {
    }
}
