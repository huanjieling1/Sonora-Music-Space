package com.example.agent.service.impl;

import com.example.agent.config.AgentProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.AgentReplyBo;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.service.AgentChatService;
import com.example.agent.service.AssistantAgent;
import com.example.agent.tools.AgentActionContext;
import com.example.agent.tools.DevelopmentTools;
import com.example.agent.tools.MusicAgentTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

@Service
public class AgentChatServiceImpl implements AgentChatService {
    private final AgentProperties properties;
    private final ConversationStore conversationStore;
    private final DevelopmentTools developmentTools;
    private final MusicAgentTools musicAgentTools;
    private final AgentActionContext actionContext;
    private volatile AssistantAgent assistant;

    public AgentChatServiceImpl(AgentProperties properties,
                                ConversationStore conversationStore,
                                DevelopmentTools developmentTools,
                                MusicAgentTools musicAgentTools,
                                AgentActionContext actionContext) {
        this.properties = properties;
        this.conversationStore = conversationStore;
        this.developmentTools = developmentTools;
        this.musicAgentTools = musicAgentTools;
        this.actionContext = actionContext;
    }

    @Override
    public AgentReplyBo chat(Long userId, UUID conversationId, String message) {
        ensureConfigured();
        ConversationMemoryId memoryId = new ConversationMemoryId(userId, conversationId);
        actionContext.begin(memoryId);
        try {
            String answer = assistant().chat(memoryId, message);
            return new AgentReplyBo(answer, actionContext.actions());
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "GLM 模型调用失败，请检查智谱 API Key、模型配置或网络连接");
        } finally {
            actionContext.clear();
        }
    }

    private AssistantAgent assistant() {
        AssistantAgent current = assistant;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (assistant == null) {
                var builder = OpenAiChatModel.builder()
                        .apiKey(properties.apiKey())
                        .modelName(properties.modelName())
                        .temperature(properties.temperature())
                        .maxTokens(properties.maxTokens())
                        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .logRequests(properties.logRequests())
                        .logResponses(properties.logResponses());
                if (StringUtils.hasText(properties.baseUrl())) {
                    builder.baseUrl(properties.baseUrl());
                }
                assistant = AiServices.builder(AssistantAgent.class)
                        .chatModel(builder.build())
                        .chatMemoryProvider(id -> {
                            if (!(id instanceof ConversationMemoryId memoryId)) {
                                throw new IllegalArgumentException("不支持的会话记忆标识");
                            }
                            var memory = MessageWindowChatMemory.builder()
                                    .id(memoryId)
                                    .maxMessages(properties.memoryMaxMessages())
                                    .build();
                            memory.set(conversationStore.loadMemory(memoryId, properties.memoryMaxMessages()));
                            return memory;
                        })
                        .tools(developmentTools, musicAgentTools)
                        .build();
            }
            return assistant;
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GLM 尚未配置，请在 .env 中填写智谱官方 AGENT_API_KEY");
        }
        if (!StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.modelName())) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GLM 模型地址或模型名称尚未配置");
        }
    }
}
