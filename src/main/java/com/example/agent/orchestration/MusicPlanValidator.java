package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Hard boundary for model- or strategy-proposed task graphs. */
@Component
public final class MusicPlanValidator {
    private static final int MAX_TASKS = 8;

    public MusicWorkflowPlan validate(MusicWorkflowPlan plan) {
        if (plan == null) throw new IllegalArgumentException("主 Agent 没有生成任务计划");
        if (plan.tasks().size() > MAX_TASKS) {
            throw new IllegalArgumentException("主 Agent 单轮任务不能超过 " + MAX_TASKS + " 个");
        }
        Map<String, MusicWorkflowTaskSpec> tasks = new HashMap<>();
        for (MusicWorkflowTaskSpec task : plan.tasks()) {
            if (tasks.putIfAbsent(task.id(), task) != null) {
                throw new IllegalArgumentException("任务计划包含重复标识：" + task.id());
            }
        }
        for (MusicWorkflowTaskSpec task : plan.tasks()) {
            for (String dependency : task.dependencies()) {
                if (!tasks.containsKey(dependency)) {
                    throw new IllegalArgumentException("任务 " + task.id() + " 依赖不存在的任务：" + dependency);
                }
                if (task.id().equals(dependency)) {
                    throw new IllegalArgumentException("任务不能依赖自身：" + task.id());
                }
            }
        }
        Map<String, Visit> visits = new HashMap<>();
        for (String taskId : tasks.keySet()) detectCycle(taskId, tasks, visits);
        return plan;
    }

    private static void detectCycle(String taskId, Map<String, MusicWorkflowTaskSpec> tasks,
                                    Map<String, Visit> visits) {
        Visit visit = visits.get(taskId);
        if (visit == Visit.DONE) return;
        if (visit == Visit.VISITING) throw new IllegalArgumentException("任务计划存在循环依赖：" + taskId);
        visits.put(taskId, Visit.VISITING);
        for (String dependency : tasks.get(taskId).dependencies()) detectCycle(dependency, tasks, visits);
        visits.put(taskId, Visit.DONE);
    }

    private enum Visit { VISITING, DONE }
}
