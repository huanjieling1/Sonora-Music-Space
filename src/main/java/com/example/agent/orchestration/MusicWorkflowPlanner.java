package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.orchestration.workflow.MusicWorkflowHandlerRegistry;
import com.example.agent.orchestration.workflow.MusicWorkflowPlanningContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Delegates route-specific task graph construction to an auto-discovered workflow strategy. */
@Component
public class MusicWorkflowPlanner {
    private final MusicWorkflowHandlerRegistry handlers;

    /** Compatibility constructor for focused unit tests outside a Spring context. */
    public MusicWorkflowPlanner() {
        this(MusicWorkflowHandlerRegistry.builtIns());
    }

    @Autowired
    public MusicWorkflowPlanner(MusicWorkflowHandlerRegistry handlers) {
        this.handlers = handlers;
    }

    public MusicWorkflowPlan plan(MusicAgentTurn turn, MusicAgentRoute route, boolean usesProfile) {
        var context = new MusicWorkflowPlanningContext(turn, route, usesProfile);
        return handlers.require(route).plan(context);
    }
}
