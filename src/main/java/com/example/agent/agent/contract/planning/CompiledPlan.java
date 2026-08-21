package com.example.agent.agent.contract.planning;

import java.util.List;
import java.util.UUID;

/** Immutable output of the future validator/compiler, ready for generic DAG scheduling. */
public record CompiledPlan(
        String schemaVersion,
        UUID planId,
        UUID goalGraphId,
        List<PlanTask> tasks,
        List<List<String>> executionStages,
        int maxReplans
) {
    public CompiledPlan {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.strip();
        if (planId == null) throw new IllegalArgumentException("编译计划标识不能为空");
        if (goalGraphId == null) throw new IllegalArgumentException("编译计划必须关联目标图");
        tasks = PlanningModelSupport.list(tasks);
        if (tasks.isEmpty()) throw new IllegalArgumentException("编译计划至少需要一个任务");
        executionStages = PlanningModelSupport.stages(executionStages);
        if (executionStages.isEmpty() || executionStages.stream().anyMatch(List::isEmpty)) {
            throw new IllegalArgumentException("编译计划必须包含非空执行阶段");
        }
        if (maxReplans < 0 || maxReplans > 2) {
            throw new IllegalArgumentException("计划重新规划次数必须在 0 到 2 之间");
        }
    }
}
