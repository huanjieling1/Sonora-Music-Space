package com.example.agent.agent.main;

import com.example.agent.agent.capability.AgentCapabilityGateway;
import com.example.agent.agent.capability.AgentScopeDecision;
import com.example.agent.agent.capability.AgentScopeType;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.intent.MusicContextualIntentAgent;
import com.example.agent.agent.intent.MusicIntentAgent;
import com.example.agent.agent.intent.MusicIntentArbiter;
import com.example.agent.agent.intent.MusicIntentEvidenceExtractor;
import com.example.agent.agent.support.MusicSupportContextAgent;
import com.example.agent.agent.support.MusicSupportSuggestionPlanner;
import com.example.agent.config.MultiAgentProperties;
import com.example.agent.orchestration.AgentScopeRouteResolver;
import org.springframework.stereotype.Component;

/**
 * Goal-understanding child agent. It combines deterministic evidence, semantic proposals,
 * conversation follow-up context and the live capability boundary without executing tools.
 */
@Component
public final class MusicGoalUnderstandingAgent {
    private final AgentCapabilityGateway capabilityGateway;
    private final MusicIntentAgent intentAgent;
    private final MusicIntentEvidenceExtractor evidenceExtractor;
    private final MusicIntentArbiter intentArbiter;
    private final MusicSupportContextAgent supportContextAgent;
    private final MusicSupportSuggestionPlanner supportSuggestionPlanner;
    private final MusicContextualIntentAgent contextualIntentAgent;
    private final AgentScopeRouteResolver scopeRouteResolver;
    private final MultiAgentProperties properties;

    public MusicGoalUnderstandingAgent(AgentCapabilityGateway capabilityGateway, MusicIntentAgent intentAgent,
                                       MusicIntentEvidenceExtractor evidenceExtractor,
                                       MusicIntentArbiter intentArbiter,
                                       MusicSupportContextAgent supportContextAgent,
                                       MusicSupportSuggestionPlanner supportSuggestionPlanner,
                                       MusicContextualIntentAgent contextualIntentAgent,
                                       AgentScopeRouteResolver scopeRouteResolver,
                                       MultiAgentProperties properties) {
        this.capabilityGateway = capabilityGateway;
        this.intentAgent = intentAgent;
        this.evidenceExtractor = evidenceExtractor;
        this.intentArbiter = intentArbiter;
        this.supportContextAgent = supportContextAgent;
        this.supportSuggestionPlanner = supportSuggestionPlanner;
        this.contextualIntentAgent = contextualIntentAgent;
        this.scopeRouteResolver = scopeRouteResolver;
        this.properties = properties;
    }

    public MusicGoalUnderstanding understand(MusicAgentTurn turn) {
        var evidence = evidenceExtractor.extract(turn.request());
        MusicAgentRoute deterministicRoute = properties.enabled()
                ? intentAgent.classify(turn.request()) : MusicAgentRoute.CONVERSATION;
        if (deterministicRoute == null) deterministicRoute = MusicAgentRoute.CONVERSATION;
        MusicIntentUnderstanding proposal = properties.enabled() ? intentAgent.analyze(turn) : null;
        if (proposal == null) {
            proposal = MusicIntentUnderstanding.routed(deterministicRoute, MusicIntentDraft.unknown());
        }

        MusicSupportContext supportContext = MusicSupportContext.none();
        MusicSupportSuggestionPlan supportPlan = null;
        if (properties.enabled() && (proposal.route() == MusicAgentRoute.CONVERSATION
                || evidence.supportCandidate())) {
            MusicSupportContext analyzedSupport = supportContextAgent.analyze(turn);
            supportContext = analyzedSupport == null ? MusicSupportContext.none() : analyzedSupport;
            if (supportContext.actionable() && !supportContext.safetyConcern()) {
                supportPlan = supportSuggestionPlanner.plan(supportContext).orElse(null);
            }
        }

        var arbitration = intentArbiter.arbitrate(proposal, deterministicRoute, evidence,
                supportContext, supportPlan);
        MusicIntentUnderstanding understanding = arbitration.understanding();
        MusicAgentRoute semanticRoute = arbitration.route();

        AgentScopeDecision scope;
        MusicAgentRoute route;
        boolean usedCapabilityGateway;
        if (semanticRoute != MusicAgentRoute.CONVERSATION) {
            scope = new AgentScopeDecision(AgentScopeType.MUSIC,
                    semanticRoute == MusicAgentRoute.SUPPORTIVE_MUSIC
                            || semanticRoute == MusicAgentRoute.SUPPORT_SAFETY
                            ? "support context matched" : "semantic intent matched");
            route = semanticRoute;
            usedCapabilityGateway = false;
        } else {
            scope = capabilityGateway.classify(turn.request());
            route = scopeRouteResolver.resolve(scope.type());
            usedCapabilityGateway = true;
        }

        MusicTurnPlan followUpPlan = null;
        if (route == MusicAgentRoute.CONVERSATION && properties.enabled()
                && scope.type() == AgentScopeType.MUSIC) {
            followUpPlan = contextualIntentAgent.analyze(turn).orElse(null);
            if (followUpPlan != null) route = MusicAgentRoute.RECOMMENDATION_FOLLOW_UP;
        }
        if (understanding.route() != route && (understanding.intent().confidence() == 0
                || route == MusicAgentRoute.SUPPORTIVE_MUSIC || route == MusicAgentRoute.SUPPORT_SAFETY)) {
            understanding = MusicIntentUnderstanding.routed(route, understanding.intent());
        }

        boolean usesProfile = route == MusicAgentRoute.RECOMMENDATION_FOLLOW_UP
                || route == MusicAgentRoute.SUPPORTIVE_MUSIC
                || (route == MusicAgentRoute.MUSIC_DISCOVERY && (understanding.intent().personalized()
                || MusicIntentAgent.shouldUseRecommendationProfile(turn.request())));
        return new MusicGoalUnderstanding(understanding, route, scope, usesProfile,
                usedCapabilityGateway, followUpPlan, supportContext, supportPlan,
                arbitration.reason(), evidence.terms());
    }
}
