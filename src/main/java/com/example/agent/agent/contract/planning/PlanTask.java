package com.example.agent.agent.contract.planning;

import java.util.List;
import java.util.Map;

/** Capability-level task proposed by a planner. capabilityId is logical and never a Java method name. */
public record PlanTask(
        String id,
        String title,
        String capabilityId,
        List<String> goalIds,
        Map<String, ValueExpression> inputs,
        List<String> dependencies,
        List<ValueExpression> activationConditions,
        List<AcceptanceCriterion> acceptanceCriteria,
        int maxAttempts
) {
    /** Backward-compatible constructor for unconditional tasks. */
    public PlanTask(String id, String title, String capabilityId, List<String> goalIds,
                    Map<String, ValueExpression> inputs, List<String> dependencies,
                    List<AcceptanceCriterion> acceptanceCriteria, int maxAttempts) {
        this(id, title, capabilityId, goalIds, inputs, dependencies, List.of(),
                acceptanceCriteria, maxAttempts);
    }

    public PlanTask {
        id = PlanningModelSupport.requiredText(id, "计划任务标识不能为空");
        title = PlanningModelSupport.requiredText(title, "计划任务标题不能为空");
        capabilityId = PlanningModelSupport.requiredText(capabilityId, "计划任务能力标识不能为空");
        goalIds = PlanningModelSupport.strings(goalIds);
        if (goalIds.isEmpty()) throw new IllegalArgumentException("计划任务至少需要关联一个用户目标");
        inputs = PlanningModelSupport.map(inputs);
        dependencies = PlanningModelSupport.strings(dependencies);
        activationConditions = PlanningModelSupport.list(activationConditions);
        acceptanceCriteria = PlanningModelSupport.list(acceptanceCriteria);
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("计划任务尝试次数必须在 1 到 3 之间");
        }
    }
}
