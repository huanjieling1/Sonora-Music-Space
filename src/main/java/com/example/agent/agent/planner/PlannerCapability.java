package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.CapabilityExecutionPolicy;
import com.example.agent.agent.capability.CapabilitySchema;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;

import java.util.Set;

/** Sanitized planner-facing capability projection. Concrete tool names are deliberately absent. */
public record PlannerCapability(
        String id,
        String name,
        String description,
        Set<GoalOperation> supportedOperations,
        Set<GoalTargetType> supportedTargets,
        CapabilitySchema inputSchema,
        CapabilitySchema outputSchema,
        CapabilitySideEffect sideEffect,
        CapabilityExecutionPolicy executionPolicy
) {
    public PlannerCapability {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("规划能力标识不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("规划能力名称不能为空");
        id = id.strip();
        name = name.strip();
        description = description == null ? "" : description.strip();
        supportedOperations = supportedOperations == null ? Set.of() : Set.copyOf(supportedOperations);
        supportedTargets = supportedTargets == null ? Set.of() : Set.copyOf(supportedTargets);
        if (inputSchema == null || outputSchema == null || executionPolicy == null) {
            throw new IllegalArgumentException("规划能力必须包含输入、输出和执行策略");
        }
        sideEffect = sideEffect == null ? CapabilitySideEffect.READ_ONLY : sideEffect;
    }

    static PlannerCapability from(AgentCapabilityDefinition definition) {
        return new PlannerCapability(definition.id(), definition.name(), definition.description(),
                definition.supportedOperations(), definition.supportedTargets(),
                definition.inputSchema(), definition.outputSchema(), definition.sideEffect(),
                definition.executionPolicy());
    }
}
