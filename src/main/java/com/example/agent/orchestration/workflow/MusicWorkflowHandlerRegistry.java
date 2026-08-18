package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Auto-discovers route strategies and fails startup on gaps or duplicate ownership. */
@Component
public final class MusicWorkflowHandlerRegistry {
    private final Map<MusicAgentRoute, MusicWorkflowHandler> handlers;

    public MusicWorkflowHandlerRegistry(List<MusicWorkflowHandler> discovered) {
        if (discovered == null || discovered.isEmpty()) throw new IllegalStateException("没有发现音乐工作流 Handler");
        EnumMap<MusicAgentRoute, MusicWorkflowHandler> values = new EnumMap<>(MusicAgentRoute.class);
        for (MusicWorkflowHandler handler : discovered) {
            if (handler == null || handler.routes() == null || handler.routes().isEmpty()) {
                throw new IllegalStateException("工作流 Handler 必须声明至少一个路由");
            }
            for (MusicAgentRoute route : handler.routes()) {
                MusicWorkflowHandler previous = values.putIfAbsent(route, handler);
                if (previous != null) {
                    throw new IllegalStateException("路由 " + route + " 被重复注册到 "
                            + previous.id() + " 和 " + handler.id());
                }
            }
        }
        Set<MusicAgentRoute> missing = EnumSet.allOf(MusicAgentRoute.class);
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) throw new IllegalStateException("存在未注册的音乐工作流路由：" + missing);
        handlers = Map.copyOf(values);
    }

    public MusicWorkflowHandler require(MusicAgentRoute route) {
        MusicWorkflowHandler handler = handlers.get(route);
        if (handler == null) throw new IllegalArgumentException("没有可处理路由的工作流 Handler：" + route);
        return handler;
    }

    public MusicWorkflowPolicy policy(MusicAgentRoute route) {
        return require(route).policy(route);
    }

    public static MusicWorkflowHandlerRegistry builtIns() {
        return new MusicWorkflowHandlerRegistry(List.of(new BoundaryWorkflowHandler(),
                new ProfileWorkflowHandler(), new DiscoveryWorkflowHandler(), new SupportWorkflowHandler(),
                new CatalogWorkflowHandler(), new PlaybackWorkflowHandler(), new ConversationWorkflowHandler()));
    }
}
