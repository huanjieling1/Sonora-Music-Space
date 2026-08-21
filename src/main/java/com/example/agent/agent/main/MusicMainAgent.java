package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.capability.AgentCapabilityGateway;
import com.example.agent.agent.intent.MusicContextualIntentAgent;
import com.example.agent.agent.intent.MusicIntentAgent;
import com.example.agent.agent.intent.MusicIntentArbiter;
import com.example.agent.agent.intent.MusicIntentEvidenceExtractor;
import com.example.agent.agent.support.MusicSupportContextAgent;
import com.example.agent.agent.support.MusicSupportSuggestionPlanner;
import com.example.agent.config.MultiAgentProperties;
import com.example.agent.orchestration.AgentScopeRouteResolver;
import com.example.agent.orchestration.MusicWorkflowRun;
import com.example.agent.orchestration.MusicWorkflowSupervisor;
import com.example.agent.orchestration.runtime.MusicWorkflowExecutionContext;
import com.example.agent.orchestration.runtime.MusicWorkflowRuntimeHandlerRegistry;
import com.example.agent.orchestration.migration.MusicMigrationShadowService;
import com.example.agent.orchestration.migration.PlannerCutoverCoordinator;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Main supervisory agent: understands, plans, delegates and owns final task acceptance. */
@Component
public final class MusicMainAgent {
    private static final Logger log = LoggerFactory.getLogger(MusicMainAgent.class);

    private final MusicGoalUnderstandingAgent goalUnderstandingAgent;
    private final MusicWorkflowRuntimeHandlerRegistry runtimeHandlers;
    private final MusicWorkflowSupervisor supervisor;
    private final MusicMigrationShadowService migrationShadow;
    private final PlannerCutoverCoordinator plannerCutover;

    /** Compatibility constructor retained for focused tests and embedders. */
    public MusicMainAgent(AgentCapabilityGateway capabilityGateway, MusicIntentAgent intentAgent,
                          MusicIntentEvidenceExtractor evidenceExtractor, MusicIntentArbiter intentArbiter,
                          MusicSupportContextAgent supportContextAgent,
                          MusicSupportSuggestionPlanner supportSuggestionPlanner,
                          MusicContextualIntentAgent contextualIntentAgent,
                          MusicWorkflowRuntimeHandlerRegistry runtimeHandlers,
                          AgentScopeRouteResolver scopeRouteResolver, MusicWorkflowSupervisor supervisor,
                          MultiAgentProperties properties) {
        this(new MusicGoalUnderstandingAgent(capabilityGateway, intentAgent, evidenceExtractor, intentArbiter,
                        supportContextAgent, supportSuggestionPlanner, contextualIntentAgent,
                        scopeRouteResolver, properties), runtimeHandlers, supervisor, null, null);
    }

    public MusicMainAgent(MusicGoalUnderstandingAgent goalUnderstandingAgent,
                          MusicWorkflowRuntimeHandlerRegistry runtimeHandlers,
                          MusicWorkflowSupervisor supervisor) {
        this(goalUnderstandingAgent, runtimeHandlers, supervisor, null, null);
    }

    @Autowired
    public MusicMainAgent(MusicGoalUnderstandingAgent goalUnderstandingAgent,
                          MusicWorkflowRuntimeHandlerRegistry runtimeHandlers,
                          MusicWorkflowSupervisor supervisor,
                          MusicMigrationShadowService migrationShadow,
                          PlannerCutoverCoordinator plannerCutover) {
        this.goalUnderstandingAgent = goalUnderstandingAgent;
        this.runtimeHandlers = runtimeHandlers;
        this.supervisor = supervisor;
        this.migrationShadow = migrationShadow;
        this.plannerCutover = plannerCutover;
    }

    public MusicAgentWorkflowState run(MusicAgentTurn turn) {
        if (plannerCutover != null) {
            var resumed = plannerCutover.resumeIfWaiting(turn);
            if (resumed.isPresent()) return resumed.get();
        }
        MusicGoalUnderstanding goal = goalUnderstandingAgent.understand(turn);
        MusicAgentRoute route = goal.route();
        if (plannerCutover != null) {
            var dynamic = plannerCutover.executeIfEnabled(turn, goal);
            if (dynamic.isPresent()) {
                log.info("Main Agent completed through generic planner route={} workflow={} status={}",
                        route, dynamic.get().workflow().workflowId(), dynamic.get().workflow().status());
                return dynamic.get();
            }
        }
        var shadow = migrationShadow == null ? java.util.Optional
                .<MusicMigrationShadowService.PreparedShadow>empty() : migrationShadow.prepare(turn, goal);
        log.info("Main Agent selectedRoute={} reason={} evidence={}", route,
                goal.arbitrationReason(), goal.evidence());

        MusicWorkflowRun run = supervisor.start(turn, goal.understanding(), route, goal.usesProfile());
        MusicAgentWorkflowState state = MusicAgentWorkflowState.start(turn, goal.understanding(), "intent")
                .participated("supervisor").participated("planner");
        if (route == MusicAgentRoute.SUPPORTIVE_MUSIC || route == MusicAgentRoute.SUPPORT_SAFETY) {
            state = state.withSupport(goal.supportContext(), goal.supportPlan());
        }
        if (goal.usedCapabilityGateway()) state = state.participated("capability-gateway");

        var runtimeContext = new MusicWorkflowExecutionContext(turn, route, goal.understanding(), goal.scope(),
                goal.usesProfile(), goal.followUpPlan(), goal.supportContext(), goal.supportPlan(), state, run);
        var outcome = runtimeHandlers.require(route).execute(runtimeContext);
        state = outcome.state();
        run.finish(outcome.successful());
        state = state.withWorkflow(run.snapshot());
        MusicAgentWorkflowState completedState = state;
        if (migrationShadow != null) shadow.ifPresent(prepared -> {
            try {
                migrationShadow.compare(prepared, completedState);
            } catch (RuntimeException exception) {
                log.warn("Migration shadow comparison failed without affecting legacy response: {}",
                        exception.getMessage());
            }
        });
        log.info("Main Agent completed scope={} route={} workflow={} status={} participants={} userId={} conversationId={}",
                goal.scope().type(), state.route(), state.workflow().workflowId(), state.workflow().status(),
                state.participants(), turn.userId(), turn.conversationId());
        return state;
    }
}
