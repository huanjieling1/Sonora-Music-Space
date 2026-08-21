package com.example.agent.agent.planner;

import com.example.agent.agent.contract.planning.ValueExpression;

/** Stable structured failure returned when a typed reference cannot be resolved. */
public record ReferenceResolutionError(
        String code,
        ValueExpression.Kind expressionKind,
        String reference,
        String message
) {
    public ReferenceResolutionError {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("引用错误码不能为空");
        code = code.strip();
        reference = reference == null ? "" : reference.strip();
        message = message == null ? "" : message.strip();
    }
}
