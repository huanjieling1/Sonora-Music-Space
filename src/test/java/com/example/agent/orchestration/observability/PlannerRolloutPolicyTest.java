package com.example.agent.orchestration.observability;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.goal.DeterministicMusicGoalParser;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidationContext;
import com.example.agent.agent.planner.PlanValidator;
import com.example.agent.config.PlannerOperationsProperties;
import com.example.agent.skill.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerRolloutPolicyTest {
    private final AgentCapabilityRegistry registry = new AgentCapabilityRegistry(
            new AgentSkillRegistry(), List.of(new MusicPlanningCapabilityContributor()));

    @Test
    void gatesShadowReadOnlyMutationAndEmergencyFallbackInOrder() {
        PlannerOperationsProperties properties = new PlannerOperationsProperties();
        PlannerRolloutPolicy policy = new PlannerRolloutPolicy(properties, registry);
        CompiledPlan readOnly = plan("搜索《晴天》");
        CompiledPlan mutation = plan("搜索《晴天》，然后把这些歌加入队列");

        assertThat(policy.decide(readOnly).action()).isEqualTo(PlannerRolloutPolicy.Action.SHADOW_ONLY);

        properties.setRolloutMode(PlannerOperationsProperties.RolloutMode.READ_ONLY);
        assertThat(policy.decide(readOnly).action()).isEqualTo(PlannerRolloutPolicy.Action.EXECUTE);
        assertThat(policy.decide(mutation).action()).isEqualTo(PlannerRolloutPolicy.Action.LEGACY_FALLBACK);

        properties.setRolloutMode(PlannerOperationsProperties.RolloutMode.FULL);
        assertThat(policy.decide(mutation).action()).isEqualTo(PlannerRolloutPolicy.Action.EXECUTE);

        properties.setKillSwitch(true);
        assertThat(policy.decide(readOnly).action()).isEqualTo(PlannerRolloutPolicy.Action.LEGACY_FALLBACK);
        properties.setFallbackToLegacy(false);
        assertThat(policy.decide(readOnly).action()).isEqualTo(PlannerRolloutPolicy.Action.BLOCKED);
    }

    private CompiledPlan plan(String request) {
        var graph = new DeterministicMusicGoalParser().parse(request);
        return new PlanCompiler(new PlanValidator(registry)).compile(graph,
                new GenericPlanSynthesizer(registry).synthesize(graph),
                PlanValidationContext.standard("user-1"));
    }
}
