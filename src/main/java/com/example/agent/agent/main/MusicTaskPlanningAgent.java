package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import com.example.agent.orchestration.MusicPlanValidator;
import com.example.agent.orchestration.MusicWorkflowPlanner;
import org.springframework.stereotype.Component;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** Converts validated user intent into a bounded task DAG with explicit acceptance criteria. */
@Component
public final class MusicTaskPlanningAgent {
    private final MusicWorkflowPlanner planner;
    private final MusicPlanValidator validator;
    private final MusicTaskAcceptanceCriteriaRegistry acceptanceCriteria;

    public MusicTaskPlanningAgent(MusicWorkflowPlanner planner, MusicPlanValidator validator) {
        this(planner, validator, MusicTaskAcceptanceCriteriaRegistry.builtIns());
    }

    @Autowired
    public MusicTaskPlanningAgent(MusicWorkflowPlanner planner, MusicPlanValidator validator,
                                  MusicTaskAcceptanceCriteriaRegistry acceptanceCriteria) {
        this.planner = planner;
        this.validator = validator;
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public MusicWorkflowPlan plan(MusicAgentTurn turn, MusicIntentUnderstanding understanding,
                                  MusicAgentRoute route, boolean usesProfile) {
        MusicWorkflowPlan base = planner.plan(turn, route, usesProfile);
        List<MusicWorkflowTaskSpec> tasks = base.tasks().stream()
                .map(task -> withCriteria(task, understanding, acceptanceCriteria)).toList();
        String request = turn.request().length() > 56 ? turn.request().substring(0, 56) + "…" : turn.request();
        String goal = route == MusicAgentRoute.SCOPE_CLARIFICATION
                ? "补齐执行当前音乐请求所需的信息"
                : "完成“" + request + "”并只输出可验证结果";
        return validator.validate(new MusicWorkflowPlan(base.workflowId(), goal, route, tasks, base.maxReplans()));
    }

    private static MusicWorkflowTaskSpec withCriteria(MusicWorkflowTaskSpec task,
                                                       MusicIntentUnderstanding understanding,
                                                       MusicTaskAcceptanceCriteriaRegistry registry) {
        List<String> criteria = java.util.stream.Stream.concat(task.acceptanceCriteria().stream(),
                registry.criteria(task, understanding).stream()).distinct().limit(6).toList();
        return new MusicWorkflowTaskSpec(task.id(), task.title(), task.capabilityId(), task.assignedAgent(),
                task.dependencies(), task.maxAttempts(), criteria);
    }
}
