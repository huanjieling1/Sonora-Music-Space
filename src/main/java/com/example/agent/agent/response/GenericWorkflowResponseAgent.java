package com.example.agent.agent.response;

import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.orchestration.dag.DagExecutionSnapshot;
import org.springframework.stereotype.Component;

/** User-facing response agent for arbitrary compiled multi-goal workflows. */
@Component
public final class GenericWorkflowResponseAgent {
    private final FinalResponseGuard guard;

    public GenericWorkflowResponseAgent(FinalResponseGuard guard) {
        this.guard = guard;
    }

    public GenericWorkflowResponse respond(UserGoalGraph graph, DagExecutionSnapshot snapshot) {
        return guard.enforce(graph, snapshot);
    }
}
