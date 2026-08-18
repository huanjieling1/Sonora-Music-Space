package com.example.agent.agent.conversation;

import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.service.impl.ConversationStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MusicConversationAgentService {
    private final AgentProperties agentProperties;
    private final MultiAgentProperties multiAgentProperties;
    private final ConversationStore conversationStore;
    private volatile MusicConversationAgent agent;

    public MusicConversationAgentService(AgentProperties agentProperties,
                                         MultiAgentProperties multiAgentProperties,
                                         ConversationStore conversationStore) {
        this.agentProperties = agentProperties;
        this.multiAgentProperties = multiAgentProperties;
        this.conversationStore = conversationStore;
    }

    public String chat(ConversationMemoryId memoryId, String request) {
        return agent().chat(memoryId, request);
    }

    private MusicConversationAgent agent() {
        MusicConversationAgent current = agent;
        if (current != null) return current;
        synchronized (this) {
            if (agent == null) {
                MultiAgentProperties.Role role = multiAgentProperties.conversation();
                var builder = OpenAiChatModel.builder()
                        .apiKey(agentProperties.apiKey())
                        .modelName(role.modelOr(agentProperties.modelName()))
                        .temperature(role.temperature())
                        .maxTokens(role.maxTokens())
                        .timeout(Duration.ofSeconds(agentProperties.timeoutSeconds()))
                        .logRequests(agentProperties.logRequests())
                        .logResponses(agentProperties.logResponses());
                if (agentProperties.baseUrl() != null && !agentProperties.baseUrl().isBlank()) {
                    builder.baseUrl(agentProperties.baseUrl());
                }
                agent = AiServices.builder(MusicConversationAgent.class)
                        .chatModel(builder.build())
                        .chatMemoryProvider(id -> {
                            if (!(id instanceof ConversationMemoryId value)) {
                                throw new IllegalArgumentException("不支持的会话记忆标识");
                            }
                            var memory = MessageWindowChatMemory.builder().id(value)
                                    .maxMessages(agentProperties.memoryMaxMessages()).build();
                            memory.set(conversationStore.loadMemory(value, agentProperties.memoryMaxMessages()));
                            return memory;
                        }).build();
            }
            return agent;
        }
    }
}
