package com.example.agent.agent.planner;

import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.PlanDraft;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Validates a draft and deterministically compiles it into immutable topological execution stages. */
@Component
public final class PlanCompiler {
    private final PlanValidator validator;

    public PlanCompiler(PlanValidator validator) {
        this.validator = validator;
    }

    public CompiledPlan compile(UserGoalGraph graph, PlanDraft draft, PlanValidationContext context) {
        PlanValidationResult validation = validator.validate(graph, draft, context);
        if (!validation.valid()) throw new PlanValidationException(validation.issues());
        List<List<String>> stages = executionStages(draft.tasks());
        return new CompiledPlan(draft.schemaVersion(), draft.planId(), draft.goalGraphId(),
                List.copyOf(draft.tasks()), stages, draft.maxReplans());
    }

    static List<List<String>> executionStages(List<PlanTask> tasks) {
        LinkedHashMap<String, PlanTask> byId = new LinkedHashMap<>();
        tasks.forEach(task -> byId.put(task.id(), task));
        LinkedHashMap<String, Integer> indegree = new LinkedHashMap<>();
        LinkedHashMap<String, LinkedHashSet<String>> dependents = new LinkedHashMap<>();
        tasks.forEach(task -> {
            indegree.put(task.id(), task.dependencies().size());
            dependents.put(task.id(), new LinkedHashSet<>());
        });
        tasks.forEach(task -> task.dependencies()
                .forEach(dependency -> dependents.get(dependency).add(task.id())));

        ArrayList<List<String>> stages = new ArrayList<>();
        LinkedHashSet<String> ready = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) ready.add(entry.getKey());
        }
        int scheduled = 0;
        while (!ready.isEmpty()) {
            List<String> stage = List.copyOf(ready);
            stages.add(stage);
            scheduled += stage.size();
            LinkedHashSet<String> next = new LinkedHashSet<>();
            for (String completed : stage) {
                for (String dependent : dependents.get(completed)) {
                    int remaining = indegree.computeIfPresent(dependent, (ignored, count) -> count - 1);
                    if (remaining == 0) next.add(dependent);
                }
            }
            ready = next;
        }
        if (scheduled != tasks.size()) {
            throw new PlanValidationException(List.of(new PlanValidationIssue(
                    "CYCLIC_DEPENDENCY", "", "", "计划任务图无法完成拓扑编译")));
        }
        return stages.stream().map(List::copyOf).toList();
    }
}
