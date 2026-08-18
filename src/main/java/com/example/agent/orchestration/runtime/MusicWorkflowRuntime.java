package com.example.agent.orchestration.runtime;

import com.example.agent.agent.capability.AgentCapabilityAgent;
import com.example.agent.agent.capability.AgentScopeResponseAgent;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicTaskEvaluation;
import com.example.agent.agent.conversation.MusicConversationAgentService;
import com.example.agent.agent.execution.MusicExecutionAgent;
import com.example.agent.agent.feedback.MusicRecommendationFollowUpAgent;
import com.example.agent.agent.profile.MusicProfileAgent;
import com.example.agent.agent.profile.MusicRecommendationProfileAgent;
import com.example.agent.agent.profile.MusicProfileWorkflowChildAgent;
import com.example.agent.agent.response.MusicResponseAgent;
import com.example.agent.agent.response.MusicResponseWorkflowChildAgent;
import com.example.agent.agent.contract.MusicResponseTaskMode;
import com.example.agent.agent.support.MusicSupportResponseAgent;
import com.example.agent.orchestration.MusicWorkflowRun;
import com.example.agent.orchestration.MusicWorkflowSupervisor;
import com.example.agent.orchestration.MusicScheduledTaskExecution;
import com.example.agent.orchestration.MusicWorkflowChildAgentRegistry;
import com.example.agent.orchestration.MusicWorkflowTaskScheduler;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.execution.MusicExecutionWorkflowChildAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Shared bounded operations used by runtime handlers; it centralizes retry and verification semantics. */
@Component
public final class MusicWorkflowRuntime {
    private final AgentCapabilityAgent capabilityAgent;
    private final AgentScopeResponseAgent scopeResponseAgent;
    private final MusicRecommendationFollowUpAgent followUpAgent;
    private final MusicWorkflowSupervisor supervisor;
    private final MusicWorkflowTaskScheduler taskScheduler;

    /** Compatibility constructor used by focused tests. */
    public MusicWorkflowRuntime(AgentCapabilityAgent capabilityAgent,
                                AgentScopeResponseAgent scopeResponseAgent,
                                MusicRecommendationFollowUpAgent followUpAgent,
                                MusicProfileAgent profileAgent,
                                MusicRecommendationProfileAgent recommendationProfileAgent,
                                MusicExecutionAgent executionAgent,
                                MusicResponseAgent responseAgent,
                                MusicSupportResponseAgent supportResponseAgent,
                                MusicConversationAgentService conversationAgent,
                                MusicWorkflowSupervisor supervisor) {
        this(capabilityAgent, scopeResponseAgent, followUpAgent, supervisor,
                new MusicWorkflowTaskScheduler(new MusicWorkflowChildAgentRegistry(
                        List.of(new MusicExecutionWorkflowChildAgent(executionAgent),
                                new MusicProfileWorkflowChildAgent(profileAgent, recommendationProfileAgent),
                                new MusicResponseWorkflowChildAgent(responseAgent, supportResponseAgent,
                                        conversationAgent)))));
    }

    @Autowired
    public MusicWorkflowRuntime(AgentCapabilityAgent capabilityAgent,
                                AgentScopeResponseAgent scopeResponseAgent,
                                MusicRecommendationFollowUpAgent followUpAgent,
                                MusicWorkflowSupervisor supervisor,
                                MusicWorkflowTaskScheduler taskScheduler) {
        this.capabilityAgent = capabilityAgent;
        this.scopeResponseAgent = scopeResponseAgent;
        this.followUpAgent = followUpAgent;
        this.supervisor = supervisor;
        this.taskScheduler = taskScheduler;
    }

    public MusicWorkflowOutcome capability(MusicWorkflowExecutionContext context) {
        context.run().start("capability");
        var state = context.state().completed(capabilityAgent.answer(), "capability");
        context.run().complete("capability");
        return MusicWorkflowOutcome.success(state);
    }

    public MusicWorkflowOutcome outOfScope(MusicWorkflowExecutionContext context) {
        context.run().start("scope");
        var state = context.state().completed(scopeResponseAgent.outOfScope(), "scope-boundary");
        context.run().complete("scope");
        return MusicWorkflowOutcome.success(state);
    }

    public MusicWorkflowOutcome clarification(MusicWorkflowExecutionContext context) {
        context.run().start("scope");
        String answer = context.understanding().userMessage().isBlank()
                ? scopeResponseAgent.clarify() : context.understanding().userMessage();
        var state = context.state().completed(answer, "scope-boundary");
        context.run().complete("scope");
        return MusicWorkflowOutcome.success(state);
    }

    public MusicWorkflowOutcome safetySupport(MusicWorkflowExecutionContext context) {
        context.run().start("context");
        context.run().complete("context");
        context.run().start("safety");
        var state = context.state().participated("safety-gate");
        context.run().complete("safety");
        state = state.completed(scheduleResponse(context, context.turn(), MusicResponseTaskMode.SAFETY,
                Map.of()), "support-response");
        return MusicWorkflowOutcome.success(state);
    }

    public MusicWorkflowOutcome supportiveMusic(MusicWorkflowExecutionContext context) {
        context.run().start("context");
        context.run().complete("context");
        context.run().start("capability");
        MusicAgentWorkflowState state = context.state().participated("suggestion-planner");
        context.run().complete("capability");
        MusicAgentTurn supportTurn = new MusicAgentTurn(context.turn().userId(), context.turn().conversationId(),
                context.supportPlan().executionRequest(), false);
        if (context.usesProfile()) {
            state = state.withTasteContext(prepareRecommendationProfile(context, supportTurn));
        }
        ExecutionOutcome verified = executeVerified(supportTurn, context.supportPlan().executionRoute(),
                state, context.run(), context.supportPlan().expectedEvidence());
        Map<String, Object> inputs = new java.util.LinkedHashMap<>();
        inputs.put(MusicResponseWorkflowChildAgent.SUPPORT_CONTEXT, context.supportContext());
        inputs.put(MusicResponseWorkflowChildAgent.EXECUTION_RESULT, verified.result());
        state = verified.state().completed(scheduleResponse(context, supportTurn,
                MusicResponseTaskMode.SUPPORTIVE, inputs), "support-response");
        return new MusicWorkflowOutcome(state, verified.evaluation().passed());
    }

    public MusicWorkflowOutcome profile(MusicWorkflowExecutionContext context) {
        var invocation = new MusicTaskInvocation(context.run().spec("profile"), context.turn(), context.route(),
                null, Map.of(MusicProfileWorkflowChildAgent.PURPOSE, MusicProfileWorkflowChildAgent.ANALYSIS));
        var scheduled = taskScheduler.executeOnce(context.run(), invocation);
        var state = context.state().withProfile(scheduled.result().payloadAs(
                com.example.agent.agent.contract.ProfileAgentResult.class));
        state = state.completed(scheduleResponse(context, context.turn(), MusicResponseTaskMode.EXISTING_TEXT,
                Map.of(MusicResponseWorkflowChildAgent.TEXT, state.answer())), "response");
        return MusicWorkflowOutcome.success(state);
    }

    public MusicWorkflowOutcome followUp(MusicWorkflowExecutionContext context) {
        context.run().start("feedback");
        MusicAgentWorkflowState state = context.state().participated("contextual-intent");
        var outcome = followUpAgent.apply(context.turn(), context.followUpPlan());
        state = state.participated("feedback");
        context.run().complete("feedback");
        if (outcome.recommendAgain() && !outcome.recommendationRequest().isBlank()) {
            MusicAgentTurn rerun = new MusicAgentTurn(context.turn().userId(), context.turn().conversationId(),
                    outcome.recommendationRequest(), outcome.refreshBatch());
            state = state.withTasteContext(prepareRecommendationProfile(context, rerun));
            ExecutionOutcome verified = executeVerified(rerun, MusicAgentRoute.MUSIC_DISCOVERY,
                    state, context.run(), null);
            Map<String, Object> inputs = new java.util.LinkedHashMap<>();
            inputs.put(MusicResponseWorkflowChildAgent.EXECUTION_RESULT, verified.result());
            inputs.put(MusicResponseWorkflowChildAgent.PREFIX, outcome.acknowledgment());
            state = verified.state().completed(scheduleResponse(context, rerun,
                    MusicResponseTaskMode.VERIFIED_EXECUTION, inputs), "response");
            return new MusicWorkflowOutcome(state, verified.evaluation().passed());
        }
        context.run().skip("profile", "本轮只记录反馈，不需要重新读取画像");
        context.run().skip("execution", "本轮没有重新推荐任务");
        context.run().skip("verification", "没有需要验收的推荐结果");
        state = state.completed(scheduleResponse(context, context.turn(), MusicResponseTaskMode.EXISTING_TEXT,
                Map.of(MusicResponseWorkflowChildAgent.TEXT, outcome.acknowledgment())), "response");
        return MusicWorkflowOutcome.success(state);
    }

    public MusicWorkflowOutcome conversation(MusicWorkflowExecutionContext context) {
        var state = context.state().completed(scheduleResponse(context, context.turn(),
                MusicResponseTaskMode.CONVERSATION, Map.of()), "conversation");
        return MusicWorkflowOutcome.success(state);
    }

    public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
        MusicAgentWorkflowState state = context.state();
        if (context.route() == MusicAgentRoute.MUSIC_DISCOVERY && context.usesProfile()) {
            state = state.withTasteContext(prepareRecommendationProfile(context, context.turn()));
        }
        ExecutionOutcome verified = executeVerified(context.turn(), context.route(), state, context.run(), null);
        state = verified.state().completed(scheduleResponse(context, context.turn(),
                MusicResponseTaskMode.VERIFIED_EXECUTION,
                Map.of(MusicResponseWorkflowChildAgent.EXECUTION_RESULT, verified.result())), "response");
        return new MusicWorkflowOutcome(state, verified.evaluation().passed());
    }

    private ExecutionOutcome executeVerified(MusicAgentTurn turn, MusicAgentRoute route,
                                             MusicAgentWorkflowState initialState, MusicWorkflowRun run,
                                             com.example.agent.model.bo.AgentActionType expectedEvidence) {
        var spec = run.spec("execution");
        var initialInvocation = new MusicTaskInvocation(spec, turn, route, initialState.tasteContext(),
                Map.of("originalRequest", initialState.turn().request()));
        MusicScheduledTaskExecution scheduled = taskScheduler.executeVerified(run, initialInvocation,
                taskResult -> {
                    MusicExecutionResult execution = taskResult.payloadAs(MusicExecutionResult.class);
                    return expectedEvidence == null
                            ? supervisor.evaluate(execution, initialState.understanding())
                            : supervisor.evaluateSupport(execution, expectedEvidence);
                }, (evaluation, attempt) -> initialInvocation.withTurn(
                        supervisor.correct(turn, evaluation, attempt)));
        MusicTaskEvaluation evaluation = scheduled.evaluation();
        MusicExecutionResult result = scheduled.result().payloadAs(MusicExecutionResult.class);
        if (evaluation.passed()) {
            run.start("verification");
            run.verifying("verification");
            run.complete("verification");
        } else {
            run.skip("verification", evaluation.decision() == MusicTaskEvaluation.Decision.ASK_USER
                    ? "等待用户补充信息" : "执行结果未达到验收条件");
            if (result == null) {
                result = new MusicExecutionResult(route, false, evaluation.reason());
            } else if (result.successful()) {
                result = new MusicExecutionResult(route, false, evaluation.reason());
            }
        }
        return new ExecutionOutcome(result, evaluation,
                initialState.withExecution(result).participated("evaluator"));
    }

    private com.example.agent.agent.contract.UserTasteContext prepareRecommendationProfile(
            MusicWorkflowExecutionContext context, MusicAgentTurn turn) {
        var invocation = new MusicTaskInvocation(context.run().spec("profile"), turn, context.route(), null,
                Map.of(MusicProfileWorkflowChildAgent.PURPOSE, MusicProfileWorkflowChildAgent.RECOMMENDATION));
        return taskScheduler.executeOnce(context.run(), invocation).result().payloadAs(
                com.example.agent.agent.contract.UserTasteContext.class);
    }

    private String scheduleResponse(MusicWorkflowExecutionContext context, MusicAgentTurn turn,
                                    MusicResponseTaskMode mode, Map<String, Object> values) {
        Map<String, Object> inputs = new java.util.LinkedHashMap<>(values);
        inputs.put(MusicResponseWorkflowChildAgent.MODE, mode);
        var invocation = new MusicTaskInvocation(context.run().spec("response"), turn, context.route(), null, inputs);
        return taskScheduler.executeOnce(context.run(), invocation).result().payloadAs(String.class);
    }

    private record ExecutionOutcome(MusicExecutionResult result, MusicTaskEvaluation evaluation,
                                    MusicAgentWorkflowState state) {
    }
}
