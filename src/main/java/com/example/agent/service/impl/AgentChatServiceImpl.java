package com.example.agent.service.impl;

import com.example.agent.config.AgentProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.AgentReplyBo;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.service.AgentChatService;
import com.example.agent.service.AssistantAgent;
import com.example.agent.skill.AgentSkillRegistry;
import com.example.agent.tools.AgentActionContext;
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
    private final MusicAgentTools musicAgentTools;
    private final AgentActionContext actionContext;
    private final AgentSkillRegistry skillRegistry;
    private volatile AssistantAgent assistant;

    public AgentChatServiceImpl(AgentProperties properties,
                                ConversationStore conversationStore,
                                MusicAgentTools musicAgentTools,
                                AgentActionContext actionContext,
                                AgentSkillRegistry skillRegistry) {
        this.properties = properties;
        this.conversationStore = conversationStore;
        this.musicAgentTools = musicAgentTools;
        this.actionContext = actionContext;
        this.skillRegistry = skillRegistry;
    }

    @Override
    public AgentReplyBo chat(Long userId, UUID conversationId, String message) {
        ensureConfigured();
        ConversationMemoryId memoryId = new ConversationMemoryId(userId, conversationId);
        actionContext.begin(memoryId);
        try {
            String answer;
            if (MusicRequestFallback.shouldPlayRandomQqPublicPlaylist(message)) {
                answer = musicAgentTools.playRandomQqPublicPlaylist();
            } else if (MusicRequestFallback.shouldSearchQqPlaylists(message)) {
                answer = musicAgentTools.searchQqPlaylists(message);
            } else if (MusicRequestFallback.shouldSearchQqArtists(message)) {
                answer = musicAgentTools.searchQqArtists(message);
            } else {
                answer = assistant().chat(memoryId, message);
                if (actionContext.actions().isEmpty() && MusicRequestFallback.shouldSearch(message)) {
                    answer = recoverMissedMusicRequest(message);
                }
            }
            return new AgentReplyBo(answer, actionContext.actions());
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY,
                    "GLM 模型调用失败，请检查智谱 API Key、模型配置或网络连接");
        } finally {
            actionContext.clear();
        }
    }

    private String recoverMissedMusicRequest(String message) {
        String searchResult = musicAgentTools.recommendMusic(message);
        var recommendation = actionContext.actions().stream()
                .filter(action -> action.recommendation() != null)
                .reduce((left, right) -> right)
                .map(action -> action.recommendation())
                .orElse(null);
        if (recommendation == null) {
            return MusicRequestFallback.failureAnswer(searchResult);
        }
        if (recommendation.tracks().isEmpty()) {
            return StringUtils.hasText(recommendation.explanation())
                    ? recommendation.explanation()
                    : "没有找到可靠匹配的可播放歌曲，请尝试补充作品名、歌手或原声关键词。";
        }

        if (MusicRequestFallback.wantsPlayback(message)) {
            musicAgentTools.playRecommendedTrack(1);
            var first = recommendation.tracks().get(0);
            String artists = first.artists() == null || first.artists().isEmpty()
                    ? "未知歌手"
                    : String.join(" / ", first.artists());
            return "已按你的描述搜索真实曲库，并开始播放第一首匹配结果《"
                    + first.name() + "》— " + artists + "。其他结果已显示在下方卡片中。";
        }
        return "已按你的描述搜索真实曲库，匹配结果已显示在下方卡片中。";
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
                        .tools(musicAgentTools)
                        .systemMessageTransformer(skillRegistry::augmentSystemMessage)
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
