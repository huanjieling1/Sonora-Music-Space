package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public final class ConversationRuntimeHandler implements MusicWorkflowRuntimeHandler {
    private final MusicWorkflowRuntime runtime;
    public ConversationRuntimeHandler(MusicWorkflowRuntime runtime) { this.runtime = runtime; }
    @Override public String id() { return "conversation"; }
    @Override public Set<MusicAgentRoute> routes() { return Set.of(MusicAgentRoute.CONVERSATION); }
    @Override public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
        return runtime.conversation(context);
    }
}
