package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public final class ProfileRuntimeHandler implements MusicWorkflowRuntimeHandler {
    private final MusicWorkflowRuntime runtime;
    public ProfileRuntimeHandler(MusicWorkflowRuntime runtime) { this.runtime = runtime; }
    @Override public String id() { return "profile-analysis"; }
    @Override public Set<MusicAgentRoute> routes() { return Set.of(MusicAgentRoute.PROFILE_ANALYSIS); }
    @Override public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
        return runtime.profile(context);
    }
}
