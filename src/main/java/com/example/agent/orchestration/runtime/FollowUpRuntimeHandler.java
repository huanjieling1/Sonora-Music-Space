package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public final class FollowUpRuntimeHandler implements MusicWorkflowRuntimeHandler {
    private final MusicWorkflowRuntime runtime;
    public FollowUpRuntimeHandler(MusicWorkflowRuntime runtime) { this.runtime = runtime; }
    @Override public String id() { return "recommendation-follow-up"; }
    @Override public Set<MusicAgentRoute> routes() { return Set.of(MusicAgentRoute.RECOMMENDATION_FOLLOW_UP); }
    @Override public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
        return runtime.followUp(context);
    }
}
