package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects one and only one runtime collaboration strategy for every semantic route. */
@Component
public final class MusicWorkflowRuntimeHandlerRegistry {
    private final Map<MusicAgentRoute, MusicWorkflowRuntimeHandler> handlers;

    public MusicWorkflowRuntimeHandlerRegistry(List<MusicWorkflowRuntimeHandler> discovered) {
        EnumMap<MusicAgentRoute, MusicWorkflowRuntimeHandler> values = new EnumMap<>(MusicAgentRoute.class);
        for (MusicWorkflowRuntimeHandler handler : discovered == null
                ? List.<MusicWorkflowRuntimeHandler>of() : discovered) {
            if (handler == null || handler.routes() == null || handler.routes().isEmpty()) {
                throw new IllegalStateException("运行时 Handler 必须声明至少一个路由");
            }
            for (MusicAgentRoute route : handler.routes()) {
                MusicWorkflowRuntimeHandler previous = values.putIfAbsent(route, handler);
                if (previous != null) {
                    throw new IllegalStateException("运行时路由 " + route + " 被重复注册到 "
                            + previous.id() + " 和 " + handler.id());
                }
            }
        }
        Set<MusicAgentRoute> missing = EnumSet.allOf(MusicAgentRoute.class);
        missing.removeAll(values.keySet());
        if (!missing.isEmpty()) throw new IllegalStateException("存在未注册的运行时工作流路由：" + missing);
        handlers = Map.copyOf(values);
    }

    public MusicWorkflowRuntimeHandler require(MusicAgentRoute route) {
        MusicWorkflowRuntimeHandler handler = handlers.get(route);
        if (handler == null) throw new IllegalArgumentException("没有可运行路由的 Handler：" + route);
        return handler;
    }
}
