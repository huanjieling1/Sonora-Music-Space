package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.model.bo.AgentActionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.example.agent.orchestration.workflow.WorkflowPlanSupport.*;

@Component
public final class DiscoveryWorkflowHandler implements MusicWorkflowHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.MUSIC_DISCOVERY,
            MusicAgentRoute.RECOMMENDATION_FOLLOW_UP);
    private static final MusicWorkflowPolicy POLICY = MusicWorkflowPolicy.readOnly(2, true,
            Set.of(AgentActionType.SHOW_MUSIC_RESULTS));

    @Override public String id() { return "music-discovery"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }
    @Override public MusicWorkflowPolicy policy(MusicAgentRoute route) { requireSupported(route, ROUTES); return POLICY; }

    @Override
    public com.example.agent.agent.contract.MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
        requireSupported(context.route(), ROUTES);
        var tasks = withIntent();
        String dependency = "intent";
        if (context.route() == MusicAgentRoute.RECOMMENDATION_FOLLOW_UP) {
            tasks.add(task("feedback", "理解并记录本轮偏好反馈", "recommendation-follow-up",
                    "Feedback Agent", List.of("intent"), 1));
            dependency = "feedback";
        }
        if (context.usesProfile()) {
            tasks.add(task("profile", "读取可信音乐偏好", "music-profile-insight",
                    "Profile Agent", List.of(dependency), 1));
            dependency = "profile";
        }
        tasks.add(task("execution", "搜索符合要求的真实歌曲", "music-discovery",
                "Execution Agent", List.of(dependency), POLICY.maxExecutionAttempts()));
        tasks.add(task("verification", "验证并筛选推荐结果", "result-verification",
                "Evaluator", List.of("execution"), 1));
        tasks.add(task("response", "整理推荐结果", "verified-response",
                "Response Agent", List.of("verification"), 1));
        return buildPlan(context, "找到符合当前要求的真实音乐", tasks);
    }
}
