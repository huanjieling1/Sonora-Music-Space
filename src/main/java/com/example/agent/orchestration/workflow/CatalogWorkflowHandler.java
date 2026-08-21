package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.model.bo.AgentActionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.agent.orchestration.workflow.WorkflowPlanSupport.*;

@Component
public final class CatalogWorkflowHandler implements MusicWorkflowHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST,
            MusicAgentRoute.PLAYLIST_SEARCH, MusicAgentRoute.ARTIST_LOOKUP, MusicAgentRoute.QQ_TREND_DISCOVERY,
            MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE);
    private static final Map<MusicAgentRoute, Definition> DEFINITIONS = definitions();

    @Override public String id() { return "qq-catalog"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }
    @Override public MusicWorkflowPolicy policy(MusicAgentRoute route) { return definition(route).policy(); }

    @Override
    public com.example.agent.agent.contract.MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
        if (context.route() == MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE) {
            var tasks = withIntent();
            tasks.add(task("profile", "读取可审计的音乐画像", "music-profile-insight",
                    "Profile Agent", List.of("intent"), 1));
            tasks.add(task("resolution", "从画像确定目标歌手", "favorite-artist-resolution",
                    "Preference Resolution Agent", List.of("profile"), 1));
            tasks.add(task("execution", "查询目标歌手的 QQ 音乐资料", "qq-artist-discovery",
                    "Execution Agent", List.of("resolution"), 1));
            tasks.add(task("verification", "验证歌手实体与资料证据", "result-verification",
                    "Evaluator", List.of("execution"), 1));
            tasks.add(task("response", "解释推断依据并展示资料", "verified-response",
                    "Response Agent", List.of("verification"), 1));
            return buildPlan(context, "根据用户画像推断最偏好的歌手并查询真实资料", tasks);
        }
        Definition definition = definition(context.route());
        var tasks = withIntent();
        tasks.add(task("execution", definition.title(), definition.capability(), "Execution Agent",
                List.of("intent"), definition.policy().maxExecutionAttempts()));
        tasks.add(task("verification", "验证真实执行结果", "result-verification",
                "Evaluator", List.of("execution"), 1));
        tasks.add(task("response", "整理最终结果", "verified-response",
                "Response Agent", List.of("verification"), 1));
        return buildPlan(context, definition.goal(), tasks);
    }

    private static Definition definition(MusicAgentRoute route) {
        Definition result = DEFINITIONS.get(route);
        if (result == null) throw new IllegalArgumentException("Handler 不支持路由：" + route);
        return result;
    }

    private static Map<MusicAgentRoute, Definition> definitions() {
        EnumMap<MusicAgentRoute, Definition> values = new EnumMap<>(MusicAgentRoute.class);
        values.put(MusicAgentRoute.RANDOM_PUBLIC_PLAYLIST, new Definition("选择并播放公开歌单",
                "qq-public-playlists", "选择一个真实 QQ 音乐公开歌单",
                new MusicWorkflowPolicy(MusicAutonomyLevel.CONFIRM_REQUIRED, 1, false, true,
                        Set.of(AgentActionType.PLAY_TRACK))));
        values.put(MusicAgentRoute.PLAYLIST_SEARCH, new Definition("搜索 QQ 音乐公开歌单",
                "qq-public-playlists", "找到符合当前要求的真实公开歌单",
                MusicWorkflowPolicy.readOnly(2, false, Set.of(AgentActionType.SHOW_QQ_PLAYLIST_RESULTS))));
        values.put(MusicAgentRoute.ARTIST_LOOKUP, new Definition("查询 QQ 音乐艺人资料",
                "qq-artist-discovery", "找到真实的 QQ 音乐艺人资料",
                MusicWorkflowPolicy.readOnly(2, false, Set.of(AgentActionType.SHOW_QQ_ARTIST_RESULTS))));
        values.put(MusicAgentRoute.QQ_TREND_DISCOVERY, new Definition("读取并聚合 QQ 音乐榜单",
                "qq-music-trends", "用可追溯榜单回答音乐趋势问题",
                MusicWorkflowPolicy.readOnly(1, false, Set.of(AgentActionType.SHOW_QQ_CHART_RESULTS))));
        values.put(MusicAgentRoute.PERSONALIZED_ARTIST_PROFILE, new Definition("查询偏好歌手资料",
                "generic-dynamic-plan", "根据画像解析歌手并查询资料",
                MusicWorkflowPolicy.readOnly(1, true, Set.of(AgentActionType.SHOW_QQ_ARTIST_RESULTS))));
        return Map.copyOf(values);
    }

    private record Definition(String title, String capability, String goal, MusicWorkflowPolicy policy) {
    }
}
