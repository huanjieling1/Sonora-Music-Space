package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.model.bo.AgentActionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.agent.orchestration.workflow.WorkflowPlanSupport.*;

@Component
public final class PlaybackWorkflowHandler implements MusicWorkflowHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.RESULT_PLAYBACK,
            MusicAgentRoute.RESULT_NAVIGATION, MusicAgentRoute.QUEUE_CONTROL);
    private static final Map<MusicAgentRoute, Definition> DEFINITIONS = definitions();

    @Override public String id() { return "music-playback"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }
    @Override public MusicWorkflowPolicy policy(MusicAgentRoute route) { return definition(route).policy(); }

    @Override
    public com.example.agent.agent.contract.MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
        Definition definition = definition(context.route());
        var tasks = withIntent();
        tasks.add(task("execution", definition.title(), "music-playback", "Execution Agent",
                List.of("intent"), definition.policy().maxExecutionAttempts()));
        tasks.add(task("verification", "验证真实执行结果", "result-verification",
                "Evaluator", List.of("execution"), 1));
        tasks.add(task("response", "整理最终结果", "verified-response",
                "Response Agent", List.of("verification"), 1));
        return buildPlan(context, requestGoal(context), tasks);
    }

    private static Definition definition(MusicAgentRoute route) {
        Definition result = DEFINITIONS.get(route);
        if (result == null) throw new IllegalArgumentException("Handler 不支持路由：" + route);
        return result;
    }

    private static Map<MusicAgentRoute, Definition> definitions() {
        EnumMap<MusicAgentRoute, Definition> values = new EnumMap<>(MusicAgentRoute.class);
        values.put(MusicAgentRoute.RESULT_PLAYBACK, new Definition("播放选中的歌曲",
                MusicWorkflowPolicy.confirmRequired(Set.of(AgentActionType.PLAY_TRACK))));
        values.put(MusicAgentRoute.RESULT_NAVIGATION, new Definition("加载指定结果页",
                MusicWorkflowPolicy.readOnly(2, false, Set.of(AgentActionType.SHOW_MUSIC_RESULTS))));
        values.put(MusicAgentRoute.QUEUE_CONTROL, new Definition("更新播放队列",
                MusicWorkflowPolicy.confirmRequired(Set.of(AgentActionType.QUEUE_MUSIC_RESULTS))));
        return Map.copyOf(values);
    }

    private record Definition(String title, MusicWorkflowPolicy policy) {
    }
}
