package com.example.agent.agent.contract;

import java.util.List;

/** Public task goal and deterministic execution policy; never contains hidden reasoning. */
public record MusicWorkflowTaskSpec(
        String id,
        String title,
        String capabilityId,
        String assignedAgent,
        List<String> dependencies,
        int maxAttempts,
        List<String> acceptanceCriteria
) {
    public MusicWorkflowTaskSpec(String id, String title, String capabilityId, String assignedAgent,
                                 List<String> dependencies, int maxAttempts) {
        this(id, title, capabilityId, assignedAgent, dependencies, maxAttempts, List.of());
    }

    public MusicWorkflowTaskSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("任务标识不能为空");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("任务标题不能为空");
        if (capabilityId == null || capabilityId.isBlank()) throw new IllegalArgumentException("能力标识不能为空");
        if (assignedAgent == null || assignedAgent.isBlank()) throw new IllegalArgumentException("负责 Agent 不能为空");
        id = id.strip();
        title = title.strip();
        capabilityId = capabilityId.strip();
        assignedAgent = assignedAgent.strip();
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        if (maxAttempts < 1 || maxAttempts > 3) throw new IllegalArgumentException("任务尝试次数必须在 1 到 3 之间");
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : acceptanceCriteria.stream()
                .filter(java.util.Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty())
                .distinct().limit(6).toList();
    }
}
