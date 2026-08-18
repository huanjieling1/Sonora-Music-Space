package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class LlmMusicFollowUpPlanner implements MusicFollowUpPlanner {
    private final AgentProperties agentProperties;
    private final MultiAgentProperties multiAgentProperties;
    private volatile MusicFollowUpLanguageAgent agent;

    public LlmMusicFollowUpPlanner(AgentProperties agentProperties,
                                   MultiAgentProperties multiAgentProperties) {
        this.agentProperties = agentProperties;
        this.multiAgentProperties = multiAgentProperties;
    }

    @Override
    public MusicTurnPlan plan(String contextPacket) {
        return agent().plan(contextPacket);
    }

    private MusicFollowUpLanguageAgent agent() {
        MusicFollowUpLanguageAgent current = agent;
        if (current != null) return current;
        if (!StringUtils.hasText(agentProperties.apiKey()) || !StringUtils.hasText(agentProperties.baseUrl())) {
            throw new IllegalStateException("上下文意图模型尚未配置");
        }
        synchronized (this) {
            if (agent == null) {
                MultiAgentProperties.Role role = multiAgentProperties.intent();
                var model = OpenAiChatModel.builder()
                        .apiKey(agentProperties.apiKey())
                        .baseUrl(agentProperties.baseUrl())
                        .modelName(role.modelOr(agentProperties.modelName()))
                        .temperature(0.0)
                        .maxTokens(role.maxTokens())
                        .timeout(Duration.ofSeconds(agentProperties.timeoutSeconds()))
                        .logRequests(agentProperties.logRequests())
                        .logResponses(agentProperties.logResponses())
                        .build();
                agent = AiServices.builder(MusicFollowUpLanguageAgent.class).chatModel(model).build();
            }
            return agent;
        }
    }
}
