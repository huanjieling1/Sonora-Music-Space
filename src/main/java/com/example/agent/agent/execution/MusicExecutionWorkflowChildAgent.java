package com.example.agent.agent.execution;

import com.example.agent.agent.contract.MusicChildAgentDescriptor;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicTaskEvidence;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.contract.MusicTaskResult;
import com.example.agent.agent.main.MusicWorkflowChildAgent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adapter that exposes the strict tool Execution Agent through the generic scheduler protocol. */
@Component
public final class MusicExecutionWorkflowChildAgent implements MusicWorkflowChildAgent {
    private static final MusicChildAgentDescriptor DESCRIPTOR = new MusicChildAgentDescriptor(
            "music-execution-agent", "Execution Agent",
            Set.of("music-discovery", "qq-public-playlists", "qq-artist-discovery", "qq-music-trends",
                    "music-playback", "proactive-music-support"), 100);

    private final MusicExecutionAgent delegate;

    public MusicExecutionWorkflowChildAgent(MusicExecutionAgent delegate) {
        this.delegate = delegate;
    }

    @Override
    public MusicChildAgentDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public MusicTaskResult execute(MusicTaskInvocation invocation) {
        MusicExecutionResult result = delegate.execute(invocation.turn(), invocation.route(),
                invocation.tasteContext());
        List<MusicTaskEvidence> evidence = result == null ? List.of() : result.evidenceTypes().stream()
                .map(type -> new MusicTaskEvidence(type.name(), "runtime-action-context", "",
                        Map.of("route", invocation.route().name())))
                .toList();
        return new MusicTaskResult(invocation.task().id(), result != null && result.successful(), result,
                evidence, result == null ? "执行 Agent 没有返回结果" : result.factualAnswer(),
                result == null ? "EMPTY_RESULT" : result.successful() ? "" : "EXECUTION_FAILED");
    }
}
