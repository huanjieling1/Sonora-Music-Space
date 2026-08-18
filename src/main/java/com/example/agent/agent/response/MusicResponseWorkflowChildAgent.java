package com.example.agent.agent.response;

import com.example.agent.agent.contract.MusicChildAgentDescriptor;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicResponseTaskMode;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicTaskEvidence;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.contract.MusicTaskResult;
import com.example.agent.agent.conversation.MusicConversationAgentService;
import com.example.agent.agent.main.MusicWorkflowChildAgent;
import com.example.agent.agent.support.MusicSupportResponseAgent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Response-only child agent. It can express verified facts but has no business tools. */
@Component
public final class MusicResponseWorkflowChildAgent implements MusicWorkflowChildAgent {
    public static final String MODE = "mode";
    public static final String EXECUTION_RESULT = "executionResult";
    public static final String SUPPORT_CONTEXT = "supportContext";
    public static final String PREFIX = "prefix";
    public static final String TEXT = "text";

    private static final MusicChildAgentDescriptor DESCRIPTOR = new MusicChildAgentDescriptor(
            "music-response-agent", "Response Agent",
            Set.of("verified-response", "supportive-response", "music-conversation"), 100);

    private final MusicResponseAgent responseAgent;
    private final MusicSupportResponseAgent supportResponseAgent;
    private final MusicConversationAgentService conversationAgent;

    public MusicResponseWorkflowChildAgent(MusicResponseAgent responseAgent,
                                           MusicSupportResponseAgent supportResponseAgent,
                                           MusicConversationAgentService conversationAgent) {
        this.responseAgent = responseAgent;
        this.supportResponseAgent = supportResponseAgent;
        this.conversationAgent = conversationAgent;
    }

    @Override
    public MusicChildAgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public MusicTaskResult execute(MusicTaskInvocation invocation) {
        MusicResponseTaskMode mode = mode(invocation.inputs().get(MODE));
        String answer = switch (mode) {
            case VERIFIED_EXECUTION -> prefix(invocation) + responseAgent.respond(
                    value(invocation, EXECUTION_RESULT, MusicExecutionResult.class));
            case SUPPORTIVE -> supportResponseAgent.respond(
                    value(invocation, SUPPORT_CONTEXT, MusicSupportContext.class),
                    value(invocation, EXECUTION_RESULT, MusicExecutionResult.class));
            case SAFETY -> supportResponseAgent.safetyResponse();
            case CONVERSATION -> conversationAgent.chat(invocation.turn().memoryId(), invocation.turn().request());
            case EXISTING_TEXT -> String.valueOf(invocation.inputs().getOrDefault(TEXT, ""));
        };
        boolean successful = answer != null && !answer.isBlank();
        return new MusicTaskResult(invocation.task().id(), successful, answer,
                successful ? List.of(new MusicTaskEvidence("VERIFIED_RESPONSE", "response-agent", "",
                        Map.of("mode", mode.name()))) : List.of(),
                successful ? "最终答复已整理" : "Response Agent 没有生成答复",
                successful ? "" : "EMPTY_RESPONSE");
    }

    private static MusicResponseTaskMode mode(Object raw) {
        if (raw instanceof MusicResponseTaskMode value) return value;
        if (raw instanceof String value) return MusicResponseTaskMode.valueOf(value);
        throw new IllegalArgumentException("响应任务缺少 mode");
    }

    private static String prefix(MusicTaskInvocation invocation) {
        String value = String.valueOf(invocation.inputs().getOrDefault(PREFIX, ""));
        return value.isBlank() ? "" : value + "\n\n";
    }

    private static <T> T value(MusicTaskInvocation invocation, String key, Class<T> type) {
        Object value = invocation.inputs().get(key);
        if (!type.isInstance(value)) throw new IllegalArgumentException("响应任务缺少 " + key);
        return type.cast(value);
    }
}
