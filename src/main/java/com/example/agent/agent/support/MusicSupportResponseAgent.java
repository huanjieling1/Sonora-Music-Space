package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.config.AgentProperties;
import com.example.agent.config.MultiAgentProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.regex.Pattern;

/** Human wording around verified results; it never creates music facts or actions. */
@Component
public class MusicSupportResponseAgent {
    private static final Pattern UNSAFE_PROMISE = Pattern.compile(
            "一定会好|保证|治愈你|治疗|诊断|抑郁症|焦虑症|已经播放|正在播放");

    private final AgentProperties properties;
    private final MultiAgentProperties multiAgentProperties;
    private volatile MusicSupportResponseLanguageAgent agent;

    public MusicSupportResponseAgent() {
        this(null, null);
    }

    @Autowired
    public MusicSupportResponseAgent(AgentProperties properties,
                                     MultiAgentProperties multiAgentProperties) {
        this.properties = properties;
        this.multiAgentProperties = multiAgentProperties;
    }

    public String respond(MusicSupportContext context, MusicExecutionResult result) {
        String fallback = fallback(context, result != null && result.successful());
        if (properties == null || multiAgentProperties == null || result == null || !result.successful()
                || !StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.modelName())) {
            return fallback;
        }
        try {
            String input = "临时状态=" + context.signal() + "\n支持目标=" + context.goal()
                    + "\n声音方向=" + context.musicDirection() + "\n已验证结果=" + result.factualAnswer();
            String answer = agent().respond(input);
            if (!StringUtils.hasText(answer)) return fallback;
            String safe = answer.strip();
            if (safe.length() > 180 || UNSAFE_PROMISE.matcher(safe).find()) return fallback;
            return safe;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public String safetyResponse() {
        return "听起来你现在可能正承受非常强烈的痛苦。音乐可以陪你一会儿，但此刻更重要的是不要独自承担："
                + "请尽快联系一位你信任的人；如果你正处于立即危险中，请联系当地急救或报警服务，并离开可能伤害自己的物品或地点。";
    }

    private MusicSupportResponseLanguageAgent agent() {
        MusicSupportResponseLanguageAgent current = agent;
        if (current != null) return current;
        synchronized (this) {
            if (agent == null) {
                MultiAgentProperties.Role role = multiAgentProperties.conversation();
                var builder = OpenAiChatModel.builder()
                        .apiKey(properties.apiKey()).modelName(role.modelOr(properties.modelName()))
                        .temperature(Math.min(0.45, role.temperature())).maxTokens(Math.min(320, role.maxTokens()))
                        .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                        .logRequests(properties.logRequests()).logResponses(properties.logResponses());
                if (StringUtils.hasText(properties.baseUrl())) builder.baseUrl(properties.baseUrl());
                agent = AiServices.builder(MusicSupportResponseLanguageAgent.class)
                        .chatModel(builder.build()).build();
            }
            return agent;
        }
    }

    private static String fallback(MusicSupportContext context, boolean successful) {
        if (!successful) {
            return "我听见了，你现在不必急着把情绪整理好。音乐结果暂时没有可靠返回，我们可以换一个方向，或者先安静聊一会儿。";
        }
        return switch (context.goal()) {
            case ENERGIZE -> "听起来此刻的你需要一点向前的力量。我在下方准备了一组由平缓渐渐变明亮的真实音乐，你可以按自己的节奏挑一首。";
            case ACCOMPANY -> "我听见了，这一刻不必急着让自己振作。我在下方准备了一组有陪伴感、不过分煽情的真实音乐，希望能陪你缓一会儿。";
            default -> "我听见了，你现在不必急着把情绪整理好。我在下方准备了一组温柔、不过分沉重的真实音乐，你可以按自己的节奏慢慢听。";
        };
    }
}
