package com.example.agent.orchestration;

import com.example.agent.agent.capability.AgentScopeType;
import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** Stable boundary mapping kept separate from workflow business strategies. */
@Component
public final class AgentScopeRouteResolver {
    private final Map<AgentScopeType, MusicAgentRoute> routes;

    public AgentScopeRouteResolver() {
        EnumMap<AgentScopeType, MusicAgentRoute> values = new EnumMap<>(AgentScopeType.class);
        values.put(AgentScopeType.CAPABILITY_INQUIRY, MusicAgentRoute.CAPABILITY_INQUIRY);
        values.put(AgentScopeType.OUT_OF_SCOPE, MusicAgentRoute.OUT_OF_SCOPE);
        values.put(AgentScopeType.NEEDS_CLARIFICATION, MusicAgentRoute.SCOPE_CLARIFICATION);
        values.put(AgentScopeType.MUSIC, MusicAgentRoute.CONVERSATION);
        values.put(AgentScopeType.SOCIAL, MusicAgentRoute.CONVERSATION);
        if (values.size() != AgentScopeType.values().length) {
            throw new IllegalStateException("能力边界类型映射不完整");
        }
        routes = Map.copyOf(values);
    }

    public MusicAgentRoute resolve(AgentScopeType type) {
        MusicAgentRoute route = routes.get(type);
        if (route == null) throw new IllegalArgumentException("未知能力边界类型：" + type);
        return route;
    }
}
