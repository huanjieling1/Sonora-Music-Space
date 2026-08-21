package com.example.agent.agent.contract.planning;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * A typed, auditable input binding. Raw user requests must never be used as an implicit tool argument.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ValueExpression.Literal.class, name = "LITERAL"),
        @JsonSubTypes.Type(value = ValueExpression.UserInput.class, name = "USER_INPUT"),
        @JsonSubTypes.Type(value = ValueExpression.ProfileValue.class, name = "PROFILE_VALUE"),
        @JsonSubTypes.Type(value = ValueExpression.TaskOutput.class, name = "TASK_OUTPUT")
})
public sealed interface ValueExpression permits ValueExpression.Literal, ValueExpression.UserInput,
        ValueExpression.ProfileValue, ValueExpression.TaskOutput {

    @JsonIgnore
    Kind kind();

    ValueType valueType();

    enum Kind {
        LITERAL, USER_INPUT, PROFILE_VALUE, TASK_OUTPUT
    }

    @JsonTypeName("LITERAL")
    record Literal(ValueType valueType, Object value) implements ValueExpression {
        public Literal {
            valueType = valueType == null ? ValueType.ANY : valueType;
            value = PlanningModelSupport.immutableJsonValue(value);
        }

        @Override public Kind kind() { return Kind.LITERAL; }
    }

    @JsonTypeName("USER_INPUT")
    record UserInput(ValueType valueType, String slot, boolean required) implements ValueExpression {
        public UserInput {
            valueType = valueType == null ? ValueType.ANY : valueType;
            slot = PlanningModelSupport.requiredText(slot, "用户输入槽位不能为空");
        }

        @Override public Kind kind() { return Kind.USER_INPUT; }
    }

    @JsonTypeName("PROFILE_VALUE")
    record ProfileValue(ValueType valueType, String path) implements ValueExpression {
        public ProfileValue {
            valueType = valueType == null ? ValueType.ANY : valueType;
            path = PlanningModelSupport.requiredText(path, "画像字段路径不能为空");
        }

        @Override public Kind kind() { return Kind.PROFILE_VALUE; }
    }

    @JsonTypeName("TASK_OUTPUT")
    record TaskOutput(ValueType valueType, String taskId, String path) implements ValueExpression {
        public TaskOutput {
            valueType = valueType == null ? ValueType.ANY : valueType;
            taskId = PlanningModelSupport.requiredText(taskId, "上游任务标识不能为空");
            path = PlanningModelSupport.requiredText(path, "任务输出路径不能为空");
        }

        @Override public Kind kind() { return Kind.TASK_OUTPUT; }
    }

    static Literal literal(ValueType type, Object value) {
        return new Literal(type, value);
    }

    static UserInput userInput(ValueType type, String slot, boolean required) {
        return new UserInput(type, slot, required);
    }

    static ProfileValue profileValue(ValueType type, String path) {
        return new ProfileValue(type, path);
    }

    static TaskOutput taskOutput(ValueType type, String taskId, String path) {
        return new TaskOutput(type, taskId, path);
    }
}
