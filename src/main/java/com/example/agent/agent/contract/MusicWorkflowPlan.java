package com.example.agent.agent.contract;

import java.util.List;
import java.util.UUID;

public record MusicWorkflowPlan(
        UUID workflowId,
        String goal,
        MusicAgentRoute route,
        List<MusicWorkflowTaskSpec> tasks,
        int maxReplans
) {
    public MusicWorkflowPlan {
        workflowId = workflowId == null ? UUID.randomUUID() : workflowId;
        goal = goal == null || goal.isBlank() ? "完成当前音乐请求" : goal.strip();
        if (route == null) throw new IllegalArgumentException("工作流路由不能为空");
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        if (tasks.isEmpty()) throw new IllegalArgumentException("工作流至少需要一个任务");
        if (maxReplans < 0 || maxReplans > 1) throw new IllegalArgumentException("重新规划次数必须在 0 到 1 之间");
    }
}
