package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.example.agent.orchestration.workflow.WorkflowPlanSupport.*;

@Component
public final class ProfileWorkflowHandler implements MusicWorkflowHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.PROFILE_ANALYSIS);
    private static final MusicWorkflowPolicy POLICY = MusicWorkflowPolicy.readOnly(1, true, Set.of());

    @Override public String id() { return "profile-analysis"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }
    @Override public MusicWorkflowPolicy policy(MusicAgentRoute route) { requireSupported(route, ROUTES); return POLICY; }

    @Override
    public com.example.agent.agent.contract.MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
        requireSupported(context.route(), ROUTES);
        var tasks = withIntent();
        tasks.add(task("profile", "分析你的音乐画像", "music-profile-insight",
                "Profile Agent", List.of("intent"), 1));
        tasks.add(task("response", "整理画像结论", "verified-response",
                "Response Agent", List.of("profile"), 1));
        return buildPlan(context, "生成可信、简洁的音乐画像", tasks);
    }
}
