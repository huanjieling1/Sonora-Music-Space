package com.example.agent.orchestration.workflow;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class WorkflowPlanSupport {
    private WorkflowPlanSupport() {
    }

    static List<MusicWorkflowTaskSpec> withIntent() {
        List<MusicWorkflowTaskSpec> tasks = new ArrayList<>();
        tasks.add(task("intent", "理解你的目标", "intent-analysis", "Intent Agent", List.of(), 1));
        return tasks;
    }

    static MusicWorkflowTaskSpec task(String id, String title, String capability, String agent,
                                      List<String> dependencies, int attempts) {
        return new MusicWorkflowTaskSpec(id, title, capability, agent, dependencies, attempts);
    }

    static MusicWorkflowPlan buildPlan(MusicWorkflowPlanningContext context, String goal,
                                       List<MusicWorkflowTaskSpec> tasks) {
        return new MusicWorkflowPlan(UUID.randomUUID(), goal, context.route(), tasks, 1);
    }

    static String requestGoal(MusicWorkflowPlanningContext context) {
        String request = context.turn().request();
        return request.length() > 48 ? request.substring(0, 48) + "…" : request;
    }

    static void requireSupported(MusicAgentRoute route, java.util.Set<MusicAgentRoute> routes) {
        if (!routes.contains(route)) throw new IllegalArgumentException("Handler 不支持路由：" + route);
    }
}
