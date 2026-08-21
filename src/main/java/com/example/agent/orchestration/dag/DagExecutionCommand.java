package com.example.agent.orchestration.dag;

import com.example.agent.agent.contract.planning.CompiledPlan;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Inputs required to start a new compiled workflow. Profile data is intentionally not persisted. */
public record DagExecutionCommand(
        UUID workflowId,
        String principalId,
        String conversationId,
        CompiledPlan plan,
        Map<String, Object> userInputs,
        Object profileRoot,
        Set<String> allowedSensitiveProfilePaths,
        DagExecutionOptions options
) {
    public DagExecutionCommand {
        workflowId = workflowId == null ? UUID.randomUUID() : workflowId;
        if (principalId == null || principalId.isBlank()) throw new IllegalArgumentException("执行用户不能为空");
        principalId = principalId.strip();
        conversationId = conversationId == null ? "" : conversationId.strip();
        if (plan == null) throw new IllegalArgumentException("CompiledPlan 不能为空");
        userInputs = userInputs == null ? Map.of() : Map.copyOf(userInputs);
        allowedSensitiveProfilePaths = allowedSensitiveProfilePaths == null
                ? Set.of() : Set.copyOf(allowedSensitiveProfilePaths);
        options = options == null ? DagExecutionOptions.defaults() : options;
    }
}
