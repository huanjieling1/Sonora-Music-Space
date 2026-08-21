package com.example.agent.agent.contract.planning;

import java.util.List;
import java.util.UUID;

/** Model-proposed plan. It remains non-executable until validated and compiled. */
public record PlanDraft(
        String schemaVersion,
        UUID planId,
        UUID goalGraphId,
        List<PlanTask> tasks,
        int maxReplans
) {
    public PlanDraft {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.strip();
        planId = planId == null ? UUID.randomUUID() : planId;
        if (goalGraphId == null) throw new IllegalArgumentException("计划必须关联目标图");
        tasks = PlanningModelSupport.list(tasks);
        if (tasks.isEmpty()) throw new IllegalArgumentException("计划草案至少需要一个任务");
        if (maxReplans < 0 || maxReplans > 2) {
            throw new IllegalArgumentException("计划重新规划次数必须在 0 到 2 之间");
        }
    }
}
