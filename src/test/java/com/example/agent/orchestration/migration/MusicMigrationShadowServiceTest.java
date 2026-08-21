package com.example.agent.orchestration.migration;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.AgentScopeDecision;
import com.example.agent.agent.capability.AgentScopeType;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidator;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.skill.AgentSkillRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MusicMigrationShadowServiceTest {
    @Test
    void recordsMatchAndMismatchWithoutChangingLegacyState() {
        AgentCapabilityRegistry registry = new AgentCapabilityRegistry(new AgentSkillRegistry(),
                List.of(new MusicPlanningCapabilityContributor()));
        var adapter = new LegacyRouteGoalGraphAdapter(new com.example.agent.agent.goal.MusicGoalDecomposer());
        var service = new MusicMigrationShadowService(adapter, new GenericPlanSynthesizer(registry),
                new PlanCompiler(new PlanValidator(registry)));
        MusicAgentTurn turn = new MusicAgentTurn(1, UUID.randomUUID(), "介绍歌手 Mili 的资料");
        MusicIntentDraft intent = new MusicIntentDraft(MusicIntentDraft.Action.SEARCH,
                MusicIntentDraft.Target.ARTIST, MusicIntentDraft.Mode.EXACT,
                MusicIntentDraft.RankingMetric.NONE, MusicIntentDraft.TimeWindow.UNSPECIFIED,
                List.of(), false, List.of(), 1);
        MusicGoalUnderstanding understanding = new MusicGoalUnderstanding(
                MusicIntentUnderstanding.routed(MusicAgentRoute.ARTIST_LOOKUP, intent),
                MusicAgentRoute.ARTIST_LOOKUP, new AgentScopeDecision(AgentScopeType.MUSIC, "test"),
                false, false, MusicTurnPlan.none(), MusicSupportContext.none(), null, "test", List.of());
        var prepared = service.prepare(turn, understanding).orElseThrow();

        var matchedState = com.example.agent.agent.contract.MusicAgentWorkflowState
                .start(turn, understanding.understanding(), "intent")
                .withExecution(new MusicExecutionResult(MusicAgentRoute.ARTIST_LOOKUP, true, "found",
                        Set.of(AgentActionType.SHOW_QQ_ARTIST_RESULTS)));
        var matched = service.compare(prepared, matchedState);
        var mismatched = service.compare(prepared, com.example.agent.agent.contract.MusicAgentWorkflowState
                .start(turn, understanding.understanding(), "intent")
                .withExecution(new MusicExecutionResult(MusicAgentRoute.ARTIST_LOOKUP, true, "found")));

        assertThat(matched.compatible()).isTrue();
        assertThat(matched.dynamicCapabilities()).containsExactly("qq.artist.lookup");
        assertThat(mismatched.compatible()).isFalse();
        assertThat(mismatched.findings()).anyMatch(value -> value.contains("EVIDENCE_MISMATCH"));
        assertThat(service.recent()).containsExactly(mismatched, matched);
        assertThat(matchedState.executionResult().successful()).isTrue();
    }
}
