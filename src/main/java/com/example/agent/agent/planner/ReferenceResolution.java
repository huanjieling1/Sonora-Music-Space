package com.example.agent.agent.planner;

import com.example.agent.agent.contract.planning.ValueType;

/** Success-or-error result; missing references never escape as unstructured exceptions. */
public record ReferenceResolution(
        boolean resolved,
        Object value,
        ValueType valueType,
        ReferenceResolutionError error
) {
    public ReferenceResolution {
        valueType = valueType == null ? ValueType.ANY : valueType;
        if (resolved && error != null) throw new IllegalArgumentException("成功解析不能携带错误");
        if (!resolved && error == null) throw new IllegalArgumentException("失败解析必须携带结构化错误");
    }

    public static ReferenceResolution success(Object value, ValueType type) {
        return new ReferenceResolution(true, value, type, null);
    }

    public static ReferenceResolution failure(ReferenceResolutionError error) {
        return new ReferenceResolution(false, null, ValueType.ANY, error);
    }
}
