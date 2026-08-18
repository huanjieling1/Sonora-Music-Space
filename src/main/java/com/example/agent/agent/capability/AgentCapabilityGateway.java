package com.example.agent.agent.capability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/** Domain gate driven by the live capability registry, not a Java keyword catalog. */
@Component
public class AgentCapabilityGateway {
    private static final Pattern CAPABILITY_INQUIRY = Pattern.compile(
            "(?:你|sonora|系统|助手)?.{0,8}(?:有哪些能力|有什么能力|能做什么|可以做什么|支持什么|有哪些功能|有什么功能|会什么|怎么用|如何使用)"
                    + "|(?:能力|功能)(?:范围|清单|介绍)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SOCIAL = Pattern.compile(
            "^(?:你好|您好|嗨|hi|hello|谢谢|感谢|好的|好|明白了|再见|晚安|早上好|下午好)[！!。,.， ]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTION_REQUEST = Pattern.compile(
            "^(?:请|请你|麻烦|帮我|给我|我要|我想|能否|能不能|可以帮我|替我).+",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final AgentCapabilityRegistry registry;

    public AgentCapabilityGateway() {
        this(new AgentCapabilityRegistry());
    }

    @Autowired
    public AgentCapabilityGateway(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public AgentScopeDecision classify(String message) {
        String normalized = message == null ? "" : message.strip().toLowerCase(Locale.ROOT);
        if (CAPABILITY_INQUIRY.matcher(normalized).find()) {
            return new AgentScopeDecision(AgentScopeType.CAPABILITY_INQUIRY, "capability inquiry");
        }
        if (registry.matches(normalized)) {
            return new AgentScopeDecision(AgentScopeType.MUSIC, "runtime capability matched");
        }
        if (SOCIAL.matcher(normalized).matches()) {
            return new AgentScopeDecision(AgentScopeType.SOCIAL, "bounded social exchange");
        }
        if (ACTION_REQUEST.matcher(normalized).matches()) {
            return new AgentScopeDecision(AgentScopeType.OUT_OF_SCOPE, "no loaded capability matched action request");
        }
        return new AgentScopeDecision(AgentScopeType.NEEDS_CLARIFICATION, "no loaded capability matched");
    }
}
