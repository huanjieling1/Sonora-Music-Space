package com.example.agent.agent.planner;

import java.util.Map;
import java.util.Set;

/** Runtime identity and data scopes available while resolving one task's typed inputs. */
public record ReferenceResolutionContext(
        String consumerTaskId,
        String principalId,
        String profileOwnerPrincipalId,
        Object profileRoot,
        Map<String, Object> userInputs,
        Set<String> allowedSensitiveProfilePaths,
        TaskResultStore resultStore
) {
    public ReferenceResolutionContext {
        if (consumerTaskId == null || consumerTaskId.isBlank()) {
            throw new IllegalArgumentException("消费任务 ID 不能为空");
        }
        consumerTaskId = consumerTaskId.strip();
        principalId = principalId == null ? "" : principalId.strip();
        profileOwnerPrincipalId = profileOwnerPrincipalId == null ? "" : profileOwnerPrincipalId.strip();
        userInputs = userInputs == null ? Map.of() : Map.copyOf(userInputs);
        allowedSensitiveProfilePaths = allowedSensitiveProfilePaths == null
                ? Set.of() : allowedSensitiveProfilePaths.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::strip)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
