package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public final class ToolExecutionRuntimeHandler implements MusicWorkflowRuntimeHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST,
            MusicAgentRoute.PLAYLIST_SEARCH, MusicAgentRoute.ARTIST_LOOKUP,
            MusicAgentRoute.QQ_TREND_DISCOVERY, MusicAgentRoute.MUSIC_DISCOVERY,
            MusicAgentRoute.RESULT_PLAYBACK, MusicAgentRoute.RESULT_NAVIGATION,
            MusicAgentRoute.QUEUE_CONTROL);
    private final MusicWorkflowRuntime runtime;
    public ToolExecutionRuntimeHandler(MusicWorkflowRuntime runtime) { this.runtime = runtime; }
    @Override public String id() { return "verified-tool-execution"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }
    @Override public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
        return runtime.execute(context);
    }
}
