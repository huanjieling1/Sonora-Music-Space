package com.example.agent.orchestration;

import com.example.agent.agent.capability.AgentCapabilityAgent;
import com.example.agent.agent.capability.AgentCapabilityGateway;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.AgentScopeResponseAgent;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicFollowUpOutcome;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.ProfileAgentResult;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.agent.contract.MusicProactiveSuggestion;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicSupportSuggestionPlan;
import com.example.agent.agent.conversation.MusicConversationAgentService;
import com.example.agent.agent.execution.MusicExecutionAgent;
import com.example.agent.agent.feedback.MusicRecommendationFollowUpAgent;
import com.example.agent.agent.intent.MusicContextualIntentAgent;
import com.example.agent.agent.intent.MusicIntentArbiter;
import com.example.agent.agent.intent.MusicIntentAgent;
import com.example.agent.agent.intent.MusicIntentEvidenceExtractor;
import com.example.agent.agent.profile.MusicProfileAgent;
import com.example.agent.agent.profile.MusicRecommendationProfileAgent;
import com.example.agent.agent.response.MusicResponseAgent;
import com.example.agent.agent.support.MusicSupportContextAgent;
import com.example.agent.agent.support.MusicSupportResponseAgent;
import com.example.agent.agent.support.MusicSupportSuggestionPlanner;
import com.example.agent.config.MultiAgentProperties;
import com.example.agent.model.bo.MusicPreferenceType;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.orchestration.runtime.CapabilityRuntimeHandler;
import com.example.agent.orchestration.runtime.ConversationRuntimeHandler;
import com.example.agent.orchestration.runtime.FollowUpRuntimeHandler;
import com.example.agent.orchestration.runtime.MusicWorkflowRuntime;
import com.example.agent.orchestration.runtime.MusicWorkflowRuntimeHandlerRegistry;
import com.example.agent.orchestration.runtime.ProfileRuntimeHandler;
import com.example.agent.orchestration.runtime.ScopeRuntimeHandler;
import com.example.agent.orchestration.runtime.SupportRuntimeHandler;
import com.example.agent.orchestration.runtime.ToolExecutionRuntimeHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MusicAgentCoordinatorTest {
    @Test
    void arbitrationRejectsInventedTrendAndLetsGroundedEmotionSelectSupport() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("我有点开心");
        MusicIntentDraft inventedTrend = new MusicIntentDraft(MusicIntentDraft.Action.RECOMMEND,
                MusicIntentDraft.Target.CHART, MusicIntentDraft.Mode.TRENDING,
                MusicIntentDraft.RankingMetric.HOTNESS, MusicIntentDraft.TimeWindow.RECENT,
                List.of(), false, List.of(), 0.96, MusicIntentDraft.Domain.MUSIC);
        MusicSupportContext support = new MusicSupportContext(
                MusicSupportContext.InteractionType.SUPPORT_SEEKING,
                MusicSupportContext.EmotionalSignal.CELEBRATION,
                MusicSupportContext.SupportGoal.ENERGIZE, 0.9, "明亮、有律动感");
        MusicSupportSuggestionPlan plan = new MusicSupportSuggestionPlan("music-discovery", "音乐发现",
                MusicAgentRoute.MUSIC_DISCOVERY, "推荐明亮有律动感的真实歌曲",
                MusicAutonomyLevel.READ_ONLY, AgentActionType.SHOW_MUSIC_RESULTS, List.of());
        MusicAgentTurn supportTurn = new MusicAgentTurn(turn.userId(), turn.conversationId(),
                plan.executionRequest(), false);
        MusicExecutionResult execution = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY,
                true, "已找到适合此刻的真实歌曲", Set.of(AgentActionType.SHOW_MUSIC_RESULTS));
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.CONVERSATION);
        when(fixture.intent.analyze(turn)).thenReturn(
                MusicIntentUnderstanding.routed(MusicAgentRoute.QQ_TREND_DISCOVERY, inventedTrend));
        when(fixture.supportContext.analyze(turn)).thenReturn(support);
        when(fixture.supportPlanner.plan(support)).thenReturn(java.util.Optional.of(plan));
        when(fixture.recommendationProfile.prepare(supportTurn)).thenReturn(context());
        when(fixture.execution.execute(supportTurn, MusicAgentRoute.MUSIC_DISCOVERY, context()))
                .thenReturn(execution);
        when(fixture.supportResponse.respond(support, execution)).thenReturn("这份开心值得一段明亮的旋律。");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.route()).isEqualTo(MusicAgentRoute.SUPPORTIVE_MUSIC);
        assertThat(state.answer()).contains("开心");
        assertThat(state.workflow().goal()).doesNotContain("榜单", "趋势");
        verify(fixture.execution).execute(supportTurn, MusicAgentRoute.MUSIC_DISCOVERY, context());
    }

    @Test
    void emotionalSupportSelectsLoadedSkillExecutesReadOnlySearchAndReturnsActions() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("我现在不开心");
        MusicSupportContext support = new MusicSupportContext(
                MusicSupportContext.InteractionType.SUPPORT_SEEKING,
                MusicSupportContext.EmotionalSignal.SADNESS,
                MusicSupportContext.SupportGoal.SOOTHE, 0.9, "温柔舒缓");
        MusicSupportSuggestionPlan plan = new MusicSupportSuggestionPlan("music-discovery", "音乐发现",
                MusicAgentRoute.MUSIC_DISCOVERY, "推荐温柔舒缓的真实歌曲。",
                MusicAutonomyLevel.READ_ONLY, AgentActionType.SHOW_MUSIC_RESULTS,
                List.of(new MusicProactiveSuggestion("再安静一点", "推荐更安静的歌", "music-discovery", false)));
        MusicAgentTurn supportTurn = new MusicAgentTurn(turn.userId(), turn.conversationId(),
                plan.executionRequest(), false);
        MusicExecutionResult execution = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY,
                true, "已找到真实歌曲", Set.of(AgentActionType.SHOW_MUSIC_RESULTS));
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.CONVERSATION);
        when(fixture.supportContext.analyze(turn)).thenReturn(support);
        when(fixture.supportPlanner.plan(support)).thenReturn(java.util.Optional.of(plan));
        when(fixture.recommendationProfile.prepare(supportTurn)).thenReturn(context());
        when(fixture.execution.execute(supportTurn, MusicAgentRoute.MUSIC_DISCOVERY, context()))
                .thenReturn(execution);
        when(fixture.supportResponse.respond(support, execution)).thenReturn("我听见了，音乐在下方陪你。");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.route()).isEqualTo(MusicAgentRoute.SUPPORTIVE_MUSIC);
        assertThat(state.answer()).isEqualTo("我听见了，音乐在下方陪你。");
        assertThat(state.supportPlan()).isEqualTo(plan);
        assertThat(state.workflow().tasks()).extracting(value -> value.id()).containsExactly(
                "intent", "context", "capability", "profile", "execution", "verification", "response");
        verify(fixture.execution).execute(supportTurn, MusicAgentRoute.MUSIC_DISCOVERY, context());
        verify(fixture.conversation, never()).chat(turn.memoryId(), turn.request());
    }

    @Test
    void safetyConcernNeverInvokesMusicExecutionAsTheOnlyResponse() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("我不想活下去了");
        MusicSupportContext support = new MusicSupportContext(
                MusicSupportContext.InteractionType.SAFETY_CONCERN,
                MusicSupportContext.EmotionalSignal.SADNESS,
                MusicSupportContext.SupportGoal.SAFETY, 1, "");
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.CONVERSATION);
        when(fixture.supportContext.analyze(turn)).thenReturn(support);
        when(fixture.supportResponse.safetyResponse()).thenReturn("请立即联系可信任的人或当地急救服务。");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.route()).isEqualTo(MusicAgentRoute.SUPPORT_SAFETY);
        assertThat(state.answer()).contains("急救服务");
        verify(fixture.execution, never()).execute(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void profileAnalysisNeverInvokesExecutionTools() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("分析我的音乐画像");
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.PROFILE_ANALYSIS);
        when(fixture.profile.analyze(turn)).thenReturn(new ProfileAgentResult(context(), "画像说明", true));

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.answer()).isEqualTo("画像说明");
        assertThat(state.participants()).containsExactly("intent", "supervisor", "planner", "profile", "response");
        assertThat(state.workflow().status()).isEqualTo(com.example.agent.agent.contract.MusicWorkflowStatus.COMPLETED);
        verify(fixture.execution, never()).execute(turn, MusicAgentRoute.PROFILE_ANALYSIS, null);
    }

    @Test
    void recommendationRunsStrictExecutionThenResponseExactlyOnce() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("推荐适合夜晚的歌");
        var execution = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, true, "已找到真实歌曲");
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.MUSIC_DISCOVERY);
        when(fixture.recommendationProfile.prepare(turn)).thenReturn(context());
        when(fixture.execution.execute(turn, MusicAgentRoute.MUSIC_DISCOVERY, context())).thenReturn(execution);
        when(fixture.response.respond(execution)).thenReturn("已找到真实歌曲");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.answer()).isEqualTo("已找到真实歌曲");
        assertThat(state.participants()).containsExactly("intent", "supervisor", "planner", "profile-context", "execution", "evaluator", "response");
        verify(fixture.recommendationProfile).prepare(turn);
        verify(fixture.execution).execute(turn, MusicAgentRoute.MUSIC_DISCOVERY, context());
        verify(fixture.response).respond(execution);
        verify(fixture.profile, never()).analyze(turn);
    }

    @Test
    void exactPlaybackDoesNotReadRecommendationProfile() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("播放周杰伦的晴天");
        var execution = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, true, "开始播放");
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.MUSIC_DISCOVERY);
        when(fixture.execution.execute(turn, MusicAgentRoute.MUSIC_DISCOVERY, null)).thenReturn(execution);
        when(fixture.response.respond(execution)).thenReturn("开始播放");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.participants()).containsExactly("intent", "supervisor", "planner", "execution", "evaluator", "response");
        verify(fixture.recommendationProfile, never()).prepare(turn);
    }

    @Test
    void compoundRecommendationFollowUpAppliesFeedbackThenRerunsExactlyOnce() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("我不喜欢这些我更喜欢mili的歌");
        var plan = new MusicTurnPlan(true, true, true,
                List.of(new MusicPreferenceChange(MusicPreferenceType.ARTIST, "mili", 1, true)),
                true, "推荐 mili 的歌", true, 1, "");
        var outcome = new MusicFollowUpOutcome(true, "推荐 mili 的歌", true,
                "已记住你更喜欢 mili。", 10);
        var rerun = new MusicAgentTurn(turn.userId(), turn.conversationId(), "推荐 mili 的歌", true);
        var execution = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, true, "已找到真实歌曲");
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.CONVERSATION);
        when(fixture.contextual.analyze(turn)).thenReturn(java.util.Optional.of(plan));
        when(fixture.followUp.apply(turn, plan)).thenReturn(outcome);
        when(fixture.recommendationProfile.prepare(rerun)).thenReturn(context());
        when(fixture.execution.execute(rerun, MusicAgentRoute.MUSIC_DISCOVERY, context())).thenReturn(execution);
        when(fixture.response.respond(execution)).thenReturn("已找到真实歌曲");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.answer()).isEqualTo("已记住你更喜欢 mili。\n\n已找到真实歌曲");
        assertThat(state.participants()).containsExactly("intent", "supervisor", "planner", "capability-gateway",
                "contextual-intent", "feedback", "profile-context", "execution", "evaluator", "response");
        verify(fixture.followUp).apply(turn, plan);
        verify(fixture.execution).execute(rerun, MusicAgentRoute.MUSIC_DISCOVERY, context());
        verify(fixture.conversation, never()).chat(turn.memoryId(), turn.request());
    }

    @Test
    void capabilityInquiryIsAnsweredFromRegistryWithoutCallingLanguageModel() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("你有哪些能力");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.route()).isEqualTo(MusicAgentRoute.CAPABILITY_INQUIRY);
        assertThat(state.answer()).contains("音乐发现", "QQ 音乐公开歌单发现", "音乐画像分析")
                .doesNotContain("设置提醒", "帮助你编程");
        assertThat(state.participants()).containsExactly("intent", "supervisor", "planner", "capability-gateway", "capability");
        verify(fixture.conversation, never()).chat(turn.memoryId(), turn.request());
    }

    @Test
    void unsupportedRequestIsRejectedBeforeAnyModelOrTool() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("帮我查明天的天气");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.route()).isEqualTo(MusicAgentRoute.OUT_OF_SCOPE);
        assertThat(state.answer()).contains("超出了当前已加载的能力范围", "音乐发现");
        verify(fixture.conversation, never()).chat(turn.memoryId(), turn.request());
        verify(fixture.execution, never()).execute(turn, MusicAgentRoute.OUT_OF_SCOPE, null);
    }

    @Test
    void semanticIntentRunsBeforeCapabilityFallbackForNaturalPersonalizedRequest() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("找一些符合我口味的歌");
        var execution = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, true, "已找到真实歌曲");
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.MUSIC_DISCOVERY);
        when(fixture.recommendationProfile.prepare(turn)).thenReturn(context());
        when(fixture.execution.execute(turn, MusicAgentRoute.MUSIC_DISCOVERY, context())).thenReturn(execution);
        when(fixture.response.respond(execution)).thenReturn("已找到真实歌曲");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.route()).isEqualTo(MusicAgentRoute.MUSIC_DISCOVERY);
        assertThat(state.answer()).isEqualTo("已找到真实歌曲");
        assertThat(state.workflow().tasks()).extracting(value -> value.id())
                .containsExactly("intent", "profile", "execution", "verification", "response");
    }

    @Test
    void evaluatorRetriesTransientReadFailureOnceAndThenCompletes() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("找一些摇滚歌曲");
        var failed = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, false, "曲库网络暂时不可用");
        var recovered = new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, true, "已找到真实歌曲");
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.MUSIC_DISCOVERY);
        when(fixture.execution.execute(turn, MusicAgentRoute.MUSIC_DISCOVERY, null))
                .thenReturn(failed, recovered);
        when(fixture.response.respond(recovered)).thenReturn("已找到真实歌曲");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.answer()).isEqualTo("已找到真实歌曲");
        assertThat(state.workflow().status()).isEqualTo(com.example.agent.agent.contract.MusicWorkflowStatus.COMPLETED);
        assertThat(state.workflow().tasks()).filteredOn(value -> value.id().equals("execution"))
                .singleElement().extracting(value -> value.attempts()).isEqualTo(2);
        verify(fixture.execution, times(2)).execute(turn, MusicAgentRoute.MUSIC_DISCOVERY, null);
    }

    @Test
    void mainAgentRejectsWrongResultTypeAndReissuesCorrectedChildTask() {
        Fixture fixture = new Fixture();
        MusicAgentTurn turn = turn("五月天的歌单");
        var intent = new com.example.agent.agent.contract.MusicIntentDraft(
                com.example.agent.agent.contract.MusicIntentDraft.Action.SEARCH,
                com.example.agent.agent.contract.MusicIntentDraft.Target.PLAYLIST,
                com.example.agent.agent.contract.MusicIntentDraft.Mode.EXACT,
                com.example.agent.agent.contract.MusicIntentDraft.RankingMetric.NONE,
                com.example.agent.agent.contract.MusicIntentDraft.TimeWindow.UNSPECIFIED,
                List.of(), false, List.of(), 0.95);
        var understanding = com.example.agent.agent.contract.MusicIntentUnderstanding.routed(
                MusicAgentRoute.PLAYLIST_SEARCH, intent);
        var wrong = new MusicExecutionResult(MusicAgentRoute.PLAYLIST_SEARCH, true, "返回了歌曲",
                java.util.Set.of(com.example.agent.model.bo.AgentActionType.SHOW_MUSIC_RESULTS));
        var corrected = new MusicExecutionResult(MusicAgentRoute.PLAYLIST_SEARCH, true, "返回了真实歌单",
                java.util.Set.of(com.example.agent.model.bo.AgentActionType.SHOW_QQ_PLAYLIST_RESULTS));
        when(fixture.intent.classify(turn.request())).thenReturn(MusicAgentRoute.PLAYLIST_SEARCH);
        when(fixture.intent.analyze(turn)).thenReturn(understanding);
        when(fixture.execution.execute(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(MusicAgentRoute.PLAYLIST_SEARCH),
                org.mockito.ArgumentMatchers.isNull())).thenReturn(wrong, corrected);
        when(fixture.response.respond(corrected)).thenReturn("返回了真实歌单");

        var state = fixture.coordinator().orchestrate(turn);

        assertThat(state.answer()).isEqualTo("返回了真实歌单");
        var delegatedTurns = org.mockito.ArgumentCaptor.forClass(MusicAgentTurn.class);
        verify(fixture.execution, times(2)).execute(delegatedTurns.capture(),
                org.mockito.ArgumentMatchers.eq(MusicAgentRoute.PLAYLIST_SEARCH),
                org.mockito.ArgumentMatchers.isNull());
        assertThat(delegatedTurns.getAllValues().get(1).executionDirective())
                .contains("未通过主 Agent 验收", "真实歌单卡片");
        assertThat(state.workflow().tasks()).filteredOn(value -> value.id().equals("execution"))
                .singleElement().extracting(value -> value.attempts()).isEqualTo(2);
    }

    private static MusicAgentTurn turn(String request) {
        return new MusicAgentTurn(1, UUID.randomUUID(), request);
    }

    private static UserTasteContext context() {
        return new UserTasteContext("STABLE", "画像稳定", true, 30, 12, 1000, 0.5,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static class Fixture {
        private final MusicIntentAgent intent = mock(MusicIntentAgent.class);
        private final MusicSupportContextAgent supportContext = mock(MusicSupportContextAgent.class);
        private final MusicSupportSuggestionPlanner supportPlanner = mock(MusicSupportSuggestionPlanner.class);
        private final MusicSupportResponseAgent supportResponse = mock(MusicSupportResponseAgent.class);
        private final MusicContextualIntentAgent contextual = mock(MusicContextualIntentAgent.class);
        private final MusicRecommendationFollowUpAgent followUp = mock(MusicRecommendationFollowUpAgent.class);
        private final MusicProfileAgent profile = mock(MusicProfileAgent.class);
        private final MusicRecommendationProfileAgent recommendationProfile =
                mock(MusicRecommendationProfileAgent.class);
        private final MusicExecutionAgent execution = mock(MusicExecutionAgent.class);
        private final MusicResponseAgent response = mock(MusicResponseAgent.class);
        private final MusicConversationAgentService conversation = mock(MusicConversationAgentService.class);
        private final AgentCapabilityRegistry capabilities = new AgentCapabilityRegistry();

        private MusicAgentCoordinator coordinator() {
            var supervisor = new MusicWorkflowSupervisor(new MusicWorkflowPlanner(), new MusicTaskEvaluator());
            var runtime = new MusicWorkflowRuntime(new AgentCapabilityAgent(capabilities),
                    new AgentScopeResponseAgent(capabilities), followUp, profile, recommendationProfile,
                    execution, response, supportResponse, conversation, supervisor);
            var runtimeHandlers = new MusicWorkflowRuntimeHandlerRegistry(List.of(
                    new CapabilityRuntimeHandler(runtime), new ScopeRuntimeHandler(runtime),
                    new SupportRuntimeHandler(runtime), new ProfileRuntimeHandler(runtime),
                    new FollowUpRuntimeHandler(runtime), new ConversationRuntimeHandler(runtime),
                    new ToolExecutionRuntimeHandler(runtime)));
            var mainAgent = new com.example.agent.agent.main.MusicMainAgent(
                    new AgentCapabilityGateway(), intent, new MusicIntentEvidenceExtractor(),
                    new MusicIntentArbiter(), supportContext, supportPlanner, contextual,
                    runtimeHandlers, new AgentScopeRouteResolver(), supervisor,
                    new MultiAgentProperties(true, null, null, null));
            return new MusicAgentCoordinator(mainAgent);
        }
    }
}
