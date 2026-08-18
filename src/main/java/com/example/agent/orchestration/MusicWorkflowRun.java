package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicWorkflowSnapshot;
import com.example.agent.agent.contract.MusicWorkflowStatus;
import com.example.agent.agent.contract.MusicWorkflowTaskSnapshot;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import com.example.agent.agent.contract.MusicWorkflowTaskStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-request mutable state owned by the deterministic workflow engine. */
public final class MusicWorkflowRun {
    private final MusicWorkflowPlan plan;
    private final Map<String, MutableTask> tasks = new LinkedHashMap<>();
    private MusicWorkflowStatus status = MusicWorkflowStatus.RUNNING;

    MusicWorkflowRun(MusicWorkflowPlan plan) {
        this.plan = plan;
        for (MusicWorkflowTaskSpec spec : plan.tasks()) tasks.put(spec.id(), new MutableTask(spec));
    }

    public void start(String taskId) {
        MutableTask task = task(taskId);
        for (String dependency : task.spec.dependencies()) {
            MusicWorkflowTaskStatus dependencyStatus = task(dependency).status;
            if (dependencyStatus != MusicWorkflowTaskStatus.COMPLETED
                    && dependencyStatus != MusicWorkflowTaskStatus.SKIPPED) {
                throw new IllegalStateException("任务 " + taskId + " 的依赖尚未完成：" + dependency);
            }
        }
        task.attempts++;
        task.status = MusicWorkflowTaskStatus.RUNNING;
        task.message = "";
    }

    public MusicWorkflowTaskSpec spec(String taskId) {
        return task(taskId).spec;
    }

    public List<MusicWorkflowTaskSpec> readyTasks() {
        return tasks.values().stream()
                .filter(value -> value.status == MusicWorkflowTaskStatus.PENDING
                        || value.status == MusicWorkflowTaskStatus.RETRYING)
                .filter(value -> value.spec.dependencies().stream().allMatch(dependency -> {
                    MusicWorkflowTaskStatus dependencyStatus = task(dependency).status;
                    return dependencyStatus == MusicWorkflowTaskStatus.COMPLETED
                            || dependencyStatus == MusicWorkflowTaskStatus.SKIPPED;
                }))
                .map(value -> value.spec).toList();
    }

    public void assign(String taskId, String agentName) {
        MutableTask task = task(taskId);
        task.assignedAgent = agentName == null || agentName.isBlank()
                ? task.spec.assignedAgent() : agentName.strip();
    }

    public void verifying(String taskId) {
        task(taskId).status = MusicWorkflowTaskStatus.VERIFYING;
    }

    public void complete(String taskId) {
        MutableTask task = task(taskId);
        task.status = MusicWorkflowTaskStatus.COMPLETED;
        task.message = "";
    }

    public void retry(String taskId, String reason) {
        MutableTask task = task(taskId);
        task.status = MusicWorkflowTaskStatus.RETRYING;
        task.message = reason == null ? "准备重新执行" : reason;
    }

    public void fail(String taskId, String reason) {
        MutableTask task = task(taskId);
        task.status = MusicWorkflowTaskStatus.FAILED;
        task.message = reason == null ? "任务未通过验收" : reason;
    }

    public void skip(String taskId, String reason) {
        MutableTask task = task(taskId);
        task.status = MusicWorkflowTaskStatus.SKIPPED;
        task.message = reason == null ? "本轮无需执行" : reason;
    }

    public void waitForUser(String taskId, String question) {
        MutableTask task = task(taskId);
        task.status = MusicWorkflowTaskStatus.WAITING_USER;
        task.message = question == null ? "需要用户补充信息" : question;
        status = MusicWorkflowStatus.WAITING_USER;
    }

    public void replanning(String taskId, String reason) {
        MutableTask task = task(taskId);
        task.status = MusicWorkflowTaskStatus.RETRYING;
        task.message = reason == null ? "主 Agent 正在重新规划" : reason;
        status = MusicWorkflowStatus.REPLANNING;
    }

    public boolean canRetry(String taskId) {
        MutableTask task = task(taskId);
        return task.attempts < task.spec.maxAttempts();
    }

    public void finish(boolean successful) {
        if (status == MusicWorkflowStatus.WAITING_USER || status == MusicWorkflowStatus.REPLANNING) {
            return;
        }
        boolean failed = tasks.values().stream().anyMatch(value -> value.status == MusicWorkflowTaskStatus.FAILED);
        boolean completed = tasks.values().stream().allMatch(value -> value.status == MusicWorkflowTaskStatus.COMPLETED
                || value.status == MusicWorkflowTaskStatus.SKIPPED);
        status = successful && completed ? MusicWorkflowStatus.COMPLETED
                : failed && tasks.values().stream().anyMatch(value -> value.status == MusicWorkflowTaskStatus.COMPLETED)
                ? MusicWorkflowStatus.PARTIAL : MusicWorkflowStatus.FAILED;
    }

    public MusicWorkflowSnapshot snapshot() {
        var snapshots = tasks.values().stream().map(value -> new MusicWorkflowTaskSnapshot(
                value.spec.id(), value.spec.title(), value.assignedAgent, value.status,
                value.attempts, value.spec.maxAttempts(), value.message)).toList();
        return new MusicWorkflowSnapshot(plan.workflowId(), plan.goal(), status, snapshots);
    }

    private MutableTask task(String id) {
        MutableTask result = tasks.get(id);
        if (result == null) throw new IllegalArgumentException("未知工作流任务：" + id);
        return result;
    }

    private static final class MutableTask {
        private final MusicWorkflowTaskSpec spec;
        private MusicWorkflowTaskStatus status = MusicWorkflowTaskStatus.PENDING;
        private int attempts;
        private String message = "";
        private String assignedAgent;

        private MutableTask(MusicWorkflowTaskSpec spec) {
            this.spec = spec;
            this.assignedAgent = spec.assignedAgent();
        }
    }
}
