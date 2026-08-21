package com.example.agent.orchestration.observability;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.config.PlannerOperationsProperties;
import org.springframework.stereotype.Component;

/** Single release gate for shadow, read-only, mutation and emergency legacy fallback. */
@Component
public final class PlannerRolloutPolicy {
    private final PlannerOperationsProperties properties;
    private final AgentCapabilityRegistry registry;

    public PlannerRolloutPolicy(PlannerOperationsProperties properties, AgentCapabilityRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    public Decision decide(CompiledPlan plan) {
        if (properties.isKillSwitch()) return fallback("KILL_SWITCH");
        if (properties.getRolloutMode() == PlannerOperationsProperties.RolloutMode.SHADOW) {
            return new Decision(Action.SHADOW_ONLY, "SHADOW_MODE");
        }
        boolean mutation = plan.tasks().stream().map(task -> registry.find(task.capabilityId()).orElseThrow())
                .anyMatch(capability -> capability.sideEffect() != CapabilitySideEffect.READ_ONLY);
        if (properties.getRolloutMode() == PlannerOperationsProperties.RolloutMode.READ_ONLY && mutation) {
            return fallback("SIDE_EFFECT_NOT_ROLLED_OUT");
        }
        return new Decision(Action.EXECUTE, properties.getRolloutMode().name());
    }

    public boolean shadowEnabled() {
        return !properties.isKillSwitch();
    }

    private Decision fallback(String reason) {
        return new Decision(properties.isFallbackToLegacy() ? Action.LEGACY_FALLBACK : Action.BLOCKED, reason);
    }

    public enum Action { SHADOW_ONLY, EXECUTE, LEGACY_FALLBACK, BLOCKED }
    public record Decision(Action action, String reason) {}
}
