package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.example.agent.orchestration.workflow.WorkflowPlanSupport.*;

@Component
public final class ConversationWorkflowHandler implements MusicWorkflowHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.CONVERSATION);
    private static final MusicWorkflowPolicy POLICY = new MusicWorkflowPolicy(
            MusicAutonomyLevel.DISABLED, 1, false, false, Set.of());

    @Override public String id() { return "music-conversation"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }
    @Override public MusicWorkflowPolicy policy(MusicAgentRoute route) { requireSupported(route, ROUTES); return POLICY; }

    @Override
    public com.example.agent.agent.contract.MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
        requireSupported(context.route(), ROUTES);
        var tasks = withIntent();
        tasks.add(task("response", "组织音乐相关回答", "music-conversation",
                "Conversation Agent", List.of("intent"), 1));
        return buildPlan(context, requestGoal(context), tasks);
    }
}
