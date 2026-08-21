package com.example.agent.agent.contract.planning;

import com.example.agent.agent.capability.CapabilitySchema;

import java.util.List;

/** Immutable, schema-carrying result envelope used by the generic execution pipeline. */
public record TypedTaskResult(
        String taskId,
        boolean successful,
        CapabilitySchema outputSchema,
        Object output,
        String provider,
        String resourceId,
        List<TypedEntityReference> entities,
        List<String> evidenceIds,
        String errorCode,
        String errorMessage
) {
    public TypedTaskResult {
        taskId = PlanningModelSupport.requiredText(taskId, "类型化结果任务 ID 不能为空");
        if (outputSchema == null) throw new IllegalArgumentException("类型化结果必须携带 Output Schema");
        output = PlanningModelSupport.immutableJsonValue(output);
        provider = PlanningModelSupport.text(provider);
        resourceId = PlanningModelSupport.text(resourceId);
        entities = PlanningModelSupport.list(entities);
        evidenceIds = PlanningModelSupport.strings(evidenceIds);
        errorCode = PlanningModelSupport.text(errorCode);
        errorMessage = PlanningModelSupport.text(errorMessage);
        if (successful && output == null) throw new IllegalArgumentException("成功结果必须携带结构化输出");
        if (successful && evidenceIds.isEmpty()) throw new IllegalArgumentException("成功结果必须携带证据 ID");
        if (!successful && errorCode.isEmpty()) throw new IllegalArgumentException("失败结果必须携带错误码");
    }

    public static TypedTaskResult success(String taskId, CapabilitySchema schema, Object output,
                                          String provider, String resourceId,
                                          List<TypedEntityReference> entities, List<String> evidenceIds) {
        return new TypedTaskResult(taskId, true, schema, output, provider, resourceId,
                entities, evidenceIds, "", "");
    }

    public static TypedTaskResult failure(String taskId, CapabilitySchema schema,
                                          String errorCode, String errorMessage,
                                          List<String> evidenceIds) {
        return new TypedTaskResult(taskId, false, schema, null, "", "", List.of(),
                evidenceIds, errorCode, errorMessage);
    }
}
