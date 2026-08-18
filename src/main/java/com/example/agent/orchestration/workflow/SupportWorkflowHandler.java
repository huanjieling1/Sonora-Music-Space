package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.model.bo.AgentActionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.example.agent.orchestration.workflow.WorkflowPlanSupport.*;

@Component
public final class SupportWorkflowHandler implements MusicWorkflowHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.SUPPORTIVE_MUSIC,
            MusicAgentRoute.SUPPORT_SAFETY);

    @Override public String id() { return "music-support"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }

    @Override
    public MusicWorkflowPolicy policy(MusicAgentRoute route) {
        requireSupported(route, ROUTES);
        return route == MusicAgentRoute.SUPPORTIVE_MUSIC
                ? MusicWorkflowPolicy.readOnly(2, true, Set.of(AgentActionType.SHOW_MUSIC_RESULTS))
                : new MusicWorkflowPolicy(MusicAutonomyLevel.DISABLED, 1, false, false, Set.of());
    }

    @Override
    public com.example.agent.agent.contract.MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
        requireSupported(context.route(), ROUTES);
        var tasks = withIntent();
        tasks.add(task("context", "理解你此刻需要怎样的陪伴", "support-context",
                "Context Agent", List.of("intent"), 1));
        if (context.route() == MusicAgentRoute.SUPPORT_SAFETY) {
            tasks.add(task("safety", "优先提供安全支持", "support-safety",
                    "Safety Gate", List.of("context"), 1));
            tasks.add(task("response", "给出简短而明确的支持", "supportive-response",
                    "Support Response Agent", List.of("safety"), 1));
            return buildPlan(context, "优先帮助用户获得现实中的安全支持", tasks);
        }
        tasks.add(task("capability", "从已加载能力中选择帮助方式", "proactive-capability-selection",
                "Suggestion Planner", List.of("context"), 1));
        String dependency = "capability";
        if (context.usesProfile()) {
            tasks.add(task("profile", "读取可靠音乐偏好", "music-profile-insight",
                    "Profile Agent", List.of("capability"), 1));
            dependency = "profile";
        }
        MusicWorkflowPolicy policy = policy(context.route());
        tasks.add(task("execution", "准备适合此刻的真实音乐", "proactive-music-support",
                "Execution Agent", List.of(dependency), policy.maxExecutionAttempts()));
        tasks.add(task("verification", "验证音乐与陪伴方向", "result-verification",
                "Evaluator", List.of("execution"), 1));
        tasks.add(task("response", "整理关怀与下一步建议", "supportive-response",
                "Support Response Agent", List.of("verification"), 1));
        return buildPlan(context, "用当前已加载能力提供有分寸的音乐陪伴", tasks);
    }
}
