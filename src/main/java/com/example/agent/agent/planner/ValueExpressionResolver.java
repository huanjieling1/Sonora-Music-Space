package com.example.agent.agent.planner;

import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.contract.planning.ValueExpression;
import com.example.agent.agent.contract.planning.ValueType;
import org.springframework.stereotype.Component;

/** Resolves typed planner expressions without allowing undeclared data access. */
@Component
public final class ValueExpressionResolver {
    private final SafeJsonPath jsonPath;
    private final ProfileFieldAccessPolicy profilePolicy;

    public ValueExpressionResolver(SafeJsonPath jsonPath, ProfileFieldAccessPolicy profilePolicy) {
        this.jsonPath = jsonPath;
        this.profilePolicy = profilePolicy;
    }

    public ReferenceResolution resolve(ValueExpression expression, ReferenceResolutionContext context) {
        if (expression == null || context == null) {
            return failure("MISSING_REFERENCE_INPUT", null, "", "引用表达式和上下文不能为空");
        }
        if (expression instanceof ValueExpression.Literal literal) {
            return ReferenceResolution.success(literal.value(), literal.valueType());
        }
        if (expression instanceof ValueExpression.UserInput userInput) {
            if (!context.userInputs().containsKey(userInput.slot())) {
                return failure("USER_INPUT_NOT_FOUND", expression.kind(), userInput.slot(),
                        "用户输入槽位尚未绑定");
            }
            return checked(context.userInputs().get(userInput.slot()), userInput.valueType(),
                    expression.kind(), userInput.slot());
        }
        if (expression instanceof ValueExpression.TaskOutput output) {
            if (context.resultStore() == null) {
                return failure("RESULT_STORE_NOT_AVAILABLE", expression.kind(), output.taskId(),
                        "当前工作流没有 TaskResultStore");
            }
            ReferenceResolution lookup = context.resultStore()
                    .lookupFor(context.consumerTaskId(), output.taskId());
            if (!lookup.resolved()) return lookup;
            TypedTaskResult result = (TypedTaskResult) lookup.value();
            SafeJsonPath.JsonPathResult read = jsonPath.read(result.output(), output.path());
            if (!read.found()) {
                return failure(read.errorCode(), expression.kind(), output.taskId() + output.path(), read.message());
            }
            return checked(read.value(), output.valueType(), expression.kind(), output.taskId() + output.path());
        }
        ValueExpression.ProfileValue profile = (ValueExpression.ProfileValue) expression;
        ProfileFieldAccessPolicy.Decision decision = profilePolicy.authorize(profile.path(), context);
        if (!decision.allowed()) {
            return failure(decision.code(), expression.kind(), profile.path(), decision.message());
        }
        if (context.profileRoot() == null) {
            return failure("PROFILE_NOT_AVAILABLE", expression.kind(), profile.path(), "当前用户画像不存在");
        }
        SafeJsonPath.JsonPathResult read = jsonPath.read(context.profileRoot(), profile.path());
        if (!read.found()) {
            return failure(read.errorCode(), expression.kind(), profile.path(), read.message());
        }
        return checked(read.value(), profile.valueType(), expression.kind(), profile.path());
    }

    private static ReferenceResolution checked(Object value, ValueType expected,
                                               ValueExpression.Kind kind, String reference) {
        ValueType actual = infer(value);
        if (!compatible(actual, expected)) {
            return failure("REFERENCE_TYPE_MISMATCH", kind, reference,
                    "引用值类型 " + actual + " 与声明类型 " + expected + " 不兼容");
        }
        return ReferenceResolution.success(value, actual == ValueType.ANY ? expected : actual);
    }

    private static ValueType infer(Object value) {
        if (value == null) return ValueType.ANY;
        if (value instanceof String) return ValueType.STRING;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ValueType.INTEGER;
        }
        if (value instanceof Number) return ValueType.DECIMAL;
        if (value instanceof Boolean) return ValueType.BOOLEAN;
        if (value instanceof java.util.Map<?, ?>) return ValueType.OBJECT;
        if (value instanceof Iterable<?>) return ValueType.ARRAY;
        return ValueType.ANY;
    }

    private static boolean compatible(ValueType actual, ValueType expected) {
        return expected == ValueType.ANY || actual == ValueType.ANY || actual == expected
                || (actual == ValueType.INTEGER && expected == ValueType.DECIMAL);
    }

    private static ReferenceResolution failure(String code, ValueExpression.Kind kind,
                                               String reference, String message) {
        return ReferenceResolution.failure(new ReferenceResolutionError(code, kind, reference, message));
    }
}
