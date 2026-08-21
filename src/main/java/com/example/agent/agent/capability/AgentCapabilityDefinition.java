package com.example.agent.agent.capability;

import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;

import java.util.Set;
import java.util.List;

/** One runtime capability backed by a loaded Skill or a capability contributor. */
public record AgentCapabilityDefinition(
        String id,
        String name,
        String description,
        Set<String> tools,
        Set<String> activationTerms,
        String source,
        Set<GoalOperation> supportedOperations,
        Set<GoalTargetType> supportedTargets,
        boolean plannerVisible,
        CapabilitySchema inputSchema,
        CapabilitySchema outputSchema,
        List<CapabilityPrecondition> preconditions,
        CapabilitySideEffect sideEffect,
        CapabilityConfirmationPolicy confirmationPolicy,
        CapabilityExecutionPolicy executionPolicy,
        CapabilityEvidencePolicy evidencePolicy
) {
    /** Backward-compatible Skill/module metadata. Such broad capabilities are not exposed to the generic planner. */
    public AgentCapabilityDefinition(String id, String name, String description, Set<String> tools,
                                     Set<String> activationTerms, String source) {
        this(id, name, description, tools, activationTerms, source, Set.of(), Set.of(), false,
                CapabilitySchema.empty(id + ".legacy.input"),
                CapabilitySchema.empty(id + ".legacy.output"), List.of(),
                CapabilitySideEffect.READ_ONLY, CapabilityConfirmationPolicy.NEVER,
                CapabilityExecutionPolicy.readOnly(30, 1, 1), CapabilityEvidencePolicy.none());
    }

    /** Backward-compatible typed constructor used before operation/target routing metadata was introduced. */
    public AgentCapabilityDefinition(String id, String name, String description, Set<String> tools,
                                     Set<String> activationTerms, String source, boolean plannerVisible,
                                     CapabilitySchema inputSchema, CapabilitySchema outputSchema,
                                     List<CapabilityPrecondition> preconditions, CapabilitySideEffect sideEffect,
                                     CapabilityConfirmationPolicy confirmationPolicy,
                                     CapabilityExecutionPolicy executionPolicy,
                                     CapabilityEvidencePolicy evidencePolicy) {
        this(id, name, description, tools, activationTerms, source, Set.of(), Set.of(), plannerVisible,
                inputSchema, outputSchema, preconditions, sideEffect, confirmationPolicy,
                executionPolicy, evidencePolicy);
    }

    public AgentCapabilityDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("能力标识不能为空");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("能力名称不能为空");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("能力说明不能为空");
        id = id.strip();
        name = name.strip();
        description = description.strip();
        tools = tools == null ? Set.of() : Set.copyOf(tools);
        activationTerms = activationTerms == null ? Set.of() : Set.copyOf(activationTerms);
        source = source == null || source.isBlank() ? "runtime" : source.strip();
        supportedOperations = supportedOperations == null ? Set.of() : Set.copyOf(supportedOperations);
        supportedTargets = supportedTargets == null ? Set.of() : Set.copyOf(supportedTargets);
        if (inputSchema == null || outputSchema == null) {
            throw new IllegalArgumentException("能力输入和输出 Schema 不能为空：" + id);
        }
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
        sideEffect = sideEffect == null ? CapabilitySideEffect.READ_ONLY : sideEffect;
        confirmationPolicy = confirmationPolicy == null ? CapabilityConfirmationPolicy.NEVER : confirmationPolicy;
        executionPolicy = executionPolicy == null
                ? CapabilityExecutionPolicy.readOnly(30, 1, 1) : executionPolicy;
        evidencePolicy = evidencePolicy == null ? CapabilityEvidencePolicy.none() : evidencePolicy;
        if (sideEffect == CapabilitySideEffect.READ_ONLY
                && confirmationPolicy == CapabilityConfirmationPolicy.ALWAYS) {
            throw new IllegalArgumentException("只读能力不应强制确认：" + id);
        }
        if (sideEffect != CapabilitySideEffect.READ_ONLY && executionPolicy.maxAttempts() > 1
                && !executionPolicy.idempotent()) {
            throw new IllegalArgumentException("非幂等副作用能力不能重试：" + id);
        }
    }
}
