package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.agent.contract.MusicIntentArbitration;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentEvidence;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import org.springframework.stereotype.Component;

/** Final non-model authority for high-impact route selection. */
@Component
public final class MusicIntentArbiter {
    public MusicIntentArbitration arbitrate(MusicIntentUnderstanding proposal,
                                            MusicAgentRoute deterministicRoute,
                                            MusicIntentEvidence evidence,
                                            MusicSupportContext support,
                                            MusicSupportSuggestionPlan supportPlan) {
        MusicIntentEvidence literal = evidence == null
                ? new MusicIntentEvidence(false, false, false, false, false, false, java.util.List.of())
                : evidence;
        MusicIntentUnderstanding proposed = proposal == null
                ? MusicIntentUnderstanding.routed(normalize(deterministicRoute), MusicIntentDraft.unknown())
                : proposal;
        MusicSupportContext context = support == null ? MusicSupportContext.none() : support;

        if (context.safetyConcern() || literal.safety()) {
            return routed(MusicAgentRoute.SUPPORT_SAFETY, proposed, literal,
                    "safety evidence has highest priority");
        }

        boolean unsupportedTrend = proposed.route() == MusicAgentRoute.QQ_TREND_DISCOVERY && !literal.trend();
        MusicAgentRoute candidate = unsupportedTrend ? normalize(deterministicRoute) : proposed.route();
        MusicIntentUnderstanding validated = unsupportedTrend
                ? MusicIntentUnderstanding.routed(candidate, clearUnsupportedTrend(proposed.intent())) : proposed;

        if (!literal.explicitMusicRequest() && context.actionable()
                && supportPlan != null && supportPlan.autonomy() == MusicAutonomyLevel.READ_ONLY) {
            return routed(MusicAgentRoute.SUPPORTIVE_MUSIC, validated, literal,
                    unsupportedTrend
                            ? "unsupported trend proposal rejected; grounded support context selected"
                            : "grounded support context selected without an explicit music command");
        }

        if (unsupportedTrend) {
            return new MusicIntentArbitration(candidate, validated,
                    "trend proposal rejected because the current wording has no trend evidence", literal);
        }
        return new MusicIntentArbitration(candidate, validated,
                literal.explicitMusicRequest() ? "explicit current-turn music evidence selected"
                        : "semantic proposal accepted after evidence arbitration", literal);
    }

    private static MusicIntentArbitration routed(MusicAgentRoute route, MusicIntentUnderstanding source,
                                                  MusicIntentEvidence evidence, String reason) {
        MusicIntentDraft intent = route == MusicAgentRoute.SUPPORTIVE_MUSIC
                || route == MusicAgentRoute.SUPPORT_SAFETY
                ? clearUnsupportedTrend(source.intent()) : source.intent();
        return new MusicIntentArbitration(route, MusicIntentUnderstanding.routed(route, intent), reason, evidence);
    }

    private static MusicIntentDraft clearUnsupportedTrend(MusicIntentDraft value) {
        if (value == null) return MusicIntentDraft.unknown();
        return new MusicIntentDraft(value.action(),
                value.target() == MusicIntentDraft.Target.CHART ? MusicIntentDraft.Target.NONE : value.target(),
                value.mode() == MusicIntentDraft.Mode.TRENDING ? MusicIntentDraft.Mode.UNKNOWN : value.mode(),
                MusicIntentDraft.RankingMetric.NONE, value.timeWindow(), value.scenes(), value.personalized(),
                value.missingSlots(), value.confidence(), value.domain());
    }

    private static MusicAgentRoute normalize(MusicAgentRoute route) {
        return route == null ? MusicAgentRoute.CONVERSATION : route;
    }
}
