package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public final class CapabilityRuntimeHandler implements MusicWorkflowRuntimeHandler {
    private final MusicWorkflowRuntime runtime;
    public CapabilityRuntimeHandler(MusicWorkflowRuntime runtime) { this.runtime = runtime; }
    @Override public String id() { return "capability"; }
    @Override public Set<MusicAgentRoute> routes() { return Set.of(MusicAgentRoute.CAPABILITY_INQUIRY); }
    @Override public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
        return runtime.capability(context);
    }
}
