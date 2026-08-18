package com.example.agent.agent.profile;

import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class LlmMusicProfileNarrator implements MusicProfileNarrator {
    private final AgentProperties agentProperties;
    private final MultiAgentProperties multiAgentProperties;
    private volatile MusicProfileLanguageAgent agent;

    public LlmMusicProfileNarrator(AgentProperties agentProperties, MultiAgentProperties multiAgentProperties) {
        this.agentProperties = agentProperties;
        this.multiAgentProperties = multiAgentProperties;
    }

    @Override
    public String narrate(UserTasteContext context, String originalRequest) {
        return agent().explain(packet(context, originalRequest));
    }

    private MusicProfileLanguageAgent agent() {
        MusicProfileLanguageAgent current = agent;
        if (current != null) return current;
        if (!StringUtils.hasText(agentProperties.apiKey()) || !StringUtils.hasText(agentProperties.baseUrl())) {
            throw new IllegalStateException("画像语言模型尚未配置");
        }
        synchronized (this) {
            if (agent == null) {
                MultiAgentProperties.Role role = multiAgentProperties.profile();
                var builder = OpenAiChatModel.builder()
                        .apiKey(agentProperties.apiKey())
                        .baseUrl(agentProperties.baseUrl())
                        .modelName(role.modelOr(agentProperties.modelName()))
                        .temperature(role.temperature())
                        .maxTokens(role.maxTokens())
                        .timeout(Duration.ofSeconds(agentProperties.timeoutSeconds()))
                        .logRequests(agentProperties.logRequests())
                        .logResponses(agentProperties.logResponses());
                agent = AiServices.builder(MusicProfileLanguageAgent.class).chatModel(builder.build()).build();
            }
            return agent;
        }
    }

    static String packet(UserTasteContext context, String originalRequest) {
        StringBuilder result = new StringBuilder()
                .append("用户本次请求：").append(originalRequest).append('\n')
                .append("画像阶段：").append(context.stageLabel()).append(" [").append(context.stage()).append("]\n")
                .append("画像门槛：").append(context.profileReady() ? "已达到" : "未达到").append('\n')
                .append("有效播放：").append(context.playCount()).append(" 次\n")
                .append("不同歌曲：").append(context.uniqueTracks()).append(" 首\n")
                .append("实际收听：").append(Math.round(context.totalPlaybackMs() / 60000.0)).append(" 分钟\n")
                .append("完播率：").append(Math.round(context.completionRate() * 100)).append("%\n");
        appendSignals(result, "喜欢证据", context.likes());
        appendSignals(result, "避开证据", context.avoids());
        appendSignals(result, "已验证用户标签", context.labels());
        appendRanks(result, "最常听歌曲", context.topTracks());
        appendRanks(result, "最常听歌手", context.topArtists());
        appendRanks(result, "偏好标签", context.topTags());
        if (!context.observations().isEmpty()) {
            result.append("画像限制：\n");
            context.observations().forEach(value -> result.append("- ").append(value).append('\n'));
        }
        return result.toString();
    }

    private static void appendSignals(StringBuilder result, String title, java.util.List<UserTasteContext.Signal> values) {
        if (values.isEmpty()) return;
        result.append(title).append("：\n");
        values.forEach(value -> result.append("- [").append(value.evidenceId()).append("] ")
                .append(value.type()).append("=").append(value.value()).append("；")
                .append(value.basis()).append("；可信度 ").append(Math.round(value.confidence() * 100)).append("%\n"));
    }

    private static void appendRanks(StringBuilder result, String title, java.util.List<UserTasteContext.RankedItem> values) {
        if (values.isEmpty()) return;
        result.append(title).append("：\n");
        values.forEach(value -> result.append("- [").append(value.evidenceId()).append("] ")
                .append(value.name()).append(StringUtils.hasText(value.detail()) ? " — " + value.detail() : "")
                .append("；").append(value.count()).append(" 次\n"));
    }
}
