package com.example.agent.service.impl;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.AgentReplyBo;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.MusicProfileStoryBo;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.ProactiveSuggestionsBo;
import com.example.agent.orchestration.MusicAgentCoordinator;
import com.example.agent.agent.response.AgentResponseGuard;
import com.example.agent.service.AgentChatService;
import com.example.agent.tools.AgentActionContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AgentChatServiceImpl implements AgentChatService {
    private final AgentActionContext actionContext;
    private final MusicAgentCoordinator coordinator;
    private final AgentResponseGuard responseGuard;

    public AgentChatServiceImpl(AgentActionContext actionContext, MusicAgentCoordinator coordinator,
                                AgentResponseGuard responseGuard) {
        this.actionContext = actionContext;
        this.coordinator = coordinator;
        this.responseGuard = responseGuard;
    }

    @Override
    public AgentReplyBo chat(Long userId, UUID conversationId, String message) {
        ConversationMemoryId memoryId = new ConversationMemoryId(userId, conversationId);
        actionContext.begin(memoryId);
        try {
            var state = coordinator.orchestrate(new MusicAgentTurn(userId, conversationId, message));
            if (state.route() == MusicAgentRoute.PROFILE_ANALYSIS
                    && state.tasteContext() != null) {
                actionContext.add(AgentActionBo.showProfileStory(
                        MusicProfileStoryBo.from(state.tasteContext(), state.answer())));
            }
            if (state.supportPlan() != null && !state.supportPlan().followUps().isEmpty()) {
                actionContext.add(AgentActionBo.showProactiveSuggestions(
                        new ProactiveSuggestionsBo("接下来想怎么听", state.supportPlan().followUps())));
            }
            if (state.workflow() != null) {
                actionContext.add(AgentActionBo.showWorkflow(state.workflow()));
            }
            var actions = actionContext.actions();
            return new AgentReplyBo(responseGuard.enforce(state.route(), state.answer(), actions), actions);
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "音乐多 Agent 协作暂时失败，请检查模型配置、曲库服务或网络连接");
        } finally {
            actionContext.clear();
        }
    }
}
