package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAutonomyLevel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

import static com.example.agent.orchestration.workflow.WorkflowPlanSupport.*;

@Component
public final class BoundaryWorkflowHandler implements MusicWorkflowHandler {
    private static final Set<MusicAgentRoute> ROUTES = Set.of(MusicAgentRoute.CAPABILITY_INQUIRY,
            MusicAgentRoute.OUT_OF_SCOPE, MusicAgentRoute.SCOPE_CLARIFICATION);
    private static final MusicWorkflowPolicy POLICY = new MusicWorkflowPolicy(
            MusicAutonomyLevel.DISABLED, 1, false, false, Set.of());

    @Override public String id() { return "boundary"; }
    @Override public Set<MusicAgentRoute> routes() { return ROUTES; }
    @Override public MusicWorkflowPolicy policy(MusicAgentRoute route) { requireSupported(route, ROUTES); return POLICY; }

    @Override
    public com.example.agent.agent.contract.MusicWorkflowPlan plan(MusicWorkflowPlanningContext context) {
        requireSupported(context.route(), ROUTES);
        var tasks = withIntent();
        if (context.route() == MusicAgentRoute.CAPABILITY_INQUIRY) {
            tasks.add(task("capability", "读取当前可用能力", "capability-introspection",
                    "Capability Agent", List.of("intent"), 1));
            return buildPlan(context, "说明当前实际加载的能力", tasks);
        }
        tasks.add(task("scope", "确认可执行的能力范围", "scope-boundary",
                "Supervisor", List.of("intent"), 1));
        return buildPlan(context, requestGoal(context), tasks);
    }
}
