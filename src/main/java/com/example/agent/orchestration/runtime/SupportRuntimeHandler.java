package com.example.agent.orchestration.runtime;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public final class SupportRuntimeHandler implements MusicWorkflowRuntimeHandler {
    private final Map<MusicAgentRoute, Function<MusicWorkflowExecutionContext, MusicWorkflowOutcome>> commands;
    public SupportRuntimeHandler(MusicWorkflowRuntime runtime) {
        EnumMap<MusicAgentRoute, Function<MusicWorkflowExecutionContext, MusicWorkflowOutcome>> values =
                new EnumMap<>(MusicAgentRoute.class);
        values.put(MusicAgentRoute.SUPPORTIVE_MUSIC, runtime::supportiveMusic);
        values.put(MusicAgentRoute.SUPPORT_SAFETY, runtime::safetySupport);
        commands = Map.copyOf(values);
    }
    @Override public String id() { return "music-support"; }
    @Override public Set<MusicAgentRoute> routes() { return commands.keySet(); }
    @Override public MusicWorkflowOutcome execute(MusicWorkflowExecutionContext context) {
        Function<MusicWorkflowExecutionContext, MusicWorkflowOutcome> command = commands.get(context.route());
        if (command == null) throw new IllegalArgumentException("支持策略不支持路由：" + context.route());
        return command.apply(context);
    }
}
