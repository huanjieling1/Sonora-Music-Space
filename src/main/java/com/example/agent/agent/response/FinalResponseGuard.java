package com.example.agent.agent.response;

import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.evaluation.EvaluationDecision;
import com.example.agent.orchestration.dag.DagExecutionSnapshot;
import com.example.agent.orchestration.dag.DagTaskState;
import com.example.agent.orchestration.dag.DagTaskStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final non-model boundary: it rebuilds response artifacts only from accepted task state. */
@Component
public final class FinalResponseGuard {
    private static final String ACCEPT_CAPABILITY = "planner.goal.accept";
    private final ResponseArtifactFactory artifacts;

    public FinalResponseGuard(ResponseArtifactFactory artifacts) {
        this.artifacts = artifacts;
    }

    public GenericWorkflowResponse enforce(UserGoalGraph graph, DagExecutionSnapshot snapshot) {
        if (graph == null || snapshot == null) throw new IllegalArgumentException("最终响应需要目标图和执行快照");
        Map<String, DagTaskState> states = new LinkedHashMap<>();
        snapshot.tasks().forEach(state -> states.put(state.taskId(), state));
        boolean graphMatches = graph.graphId().equals(snapshot.plan().goalGraphId());
        ArrayList<GoalResponseSection> sections = new ArrayList<>();
        ArrayList<ResponseCardAction> actions = new ArrayList<>();

        for (GoalNode goal : graph.goals()) {
            List<PlanTask> implementations = snapshot.plan().tasks().stream()
                    .filter(task -> task.goalIds().contains(goal.id()))
                    .filter(task -> !ACCEPT_CAPABILITY.equals(task.capabilityId())).toList();
            List<PlanTask> acceptance = snapshot.plan().tasks().stream()
                    .filter(task -> task.goalIds().contains(goal.id()))
                    .filter(task -> ACCEPT_CAPABILITY.equals(task.capabilityId())).toList();
            List<PlanTask> acceptedImplementations = implementations.stream()
                    .filter(task -> accepted(states.get(task.id()))).toList();
            boolean acceptedGoal = graphMatches && !implementations.isEmpty()
                    && acceptedImplementations.size() == implementations.size()
                    && acceptance.stream().anyMatch(task -> acceptedGoal(states.get(task.id())));
            GoalResponseStatus status = status(graphMatches, implementations, acceptedImplementations,
                    acceptance, states, acceptedGoal);
            List<GroundedResponseFact> facts = graphMatches ? acceptedImplementations.stream()
                    .map(task -> artifacts.fact(goal, task, states.get(task.id()).result())).toList() : List.of();
            if (status == GoalResponseStatus.COMPLETED) {
                acceptedImplementations.stream().map(task -> artifacts.action(goal, task,
                        states.get(task.id()).result())).forEach(actions::add);
            }
            sections.add(new GoalResponseSection(goal.id(), goal.title(), status, facts,
                    statusMessage(status, implementations, states)));
        }
        String answer = render(snapshot, sections);
        return new GenericWorkflowResponse(snapshot.workflowId(), snapshot.status(), answer,
                sections, actions, true);
    }

    private static boolean accepted(DagTaskState state) {
        return state != null && state.status() == DagTaskStatus.COMPLETED
                && state.evaluation() != null && state.evaluation().decision() == EvaluationDecision.PASS
                && state.result() != null && state.result().successful();
    }

    private static boolean acceptedGoal(DagTaskState state) {
        if (!accepted(state) || !(state.result().output() instanceof Map<?, ?> output)) return false;
        return Boolean.TRUE.equals(output.get("accepted"));
    }

    private static GoalResponseStatus status(boolean graphMatches, List<PlanTask> implementations,
                                             List<PlanTask> acceptedImplementations,
                                             List<PlanTask> acceptance,
                                             Map<String, DagTaskState> states, boolean acceptedGoal) {
        if (!graphMatches) return GoalResponseStatus.FAILED;
        if (acceptedGoal) return GoalResponseStatus.COMPLETED;
        List<DagTaskState> linked = java.util.stream.Stream.concat(implementations.stream(), acceptance.stream())
                .map(task -> states.get(task.id())).filter(java.util.Objects::nonNull).toList();
        if (linked.stream().anyMatch(state -> state.status() == DagTaskStatus.WAITING_USER)) {
            return GoalResponseStatus.WAITING_USER;
        }
        if (!acceptedImplementations.isEmpty()) return GoalResponseStatus.PARTIAL;
        if (!implementations.isEmpty() && implementations.stream().map(task -> states.get(task.id()))
                .filter(java.util.Objects::nonNull).allMatch(state -> state.status() == DagTaskStatus.SKIPPED)) {
            return GoalResponseStatus.SKIPPED;
        }
        return GoalResponseStatus.FAILED;
    }

    private static String statusMessage(GoalResponseStatus status, List<PlanTask> implementations,
                                        Map<String, DagTaskState> states) {
        return switch (status) {
            case COMPLETED -> "目标已完成并通过验收。";
            case PARTIAL -> "目标仅部分完成；已完成部分保留证据，未通过部分不会作为完成结果展示。";
            case WAITING_USER -> implementations.stream().map(task -> states.get(task.id()))
                    .filter(java.util.Objects::nonNull)
                    .filter(state -> state.status() == DagTaskStatus.WAITING_USER)
                    .map(DagTaskState::message).filter(value -> value != null && !value.isBlank())
                    .findFirst().orElse("目标正在等待用户补充信息或确认。");
            case SKIPPED -> "目标对应分支已跳过。";
            case FAILED -> "目标未完成，失败任务的结果未进入最终回复。";
        };
    }

    private static String render(DagExecutionSnapshot snapshot, List<GoalResponseSection> sections) {
        StringBuilder answer = new StringBuilder();
        answer.append("工作流状态：").append(snapshot.status()).append("。\n");
        for (int index = 0; index < sections.size(); index++) {
            GoalResponseSection section = sections.get(index);
            answer.append(index + 1).append(". ").append(statusMark(section.status()))
                    .append(' ').append(section.title()).append("：");
            if (section.facts().isEmpty()) {
                answer.append(section.message());
            } else {
                for (int factIndex = 0; factIndex < section.facts().size(); factIndex++) {
                    GroundedResponseFact fact = section.facts().get(factIndex);
                    if (factIndex > 0) answer.append(' ');
                    answer.append(kindMark(fact.kind())).append(fact.statement());
                }
                if (section.status() != GoalResponseStatus.COMPLETED) answer.append(' ').append(section.message());
            }
            if (index + 1 < sections.size()) answer.append('\n');
        }
        return answer.toString();
    }

    private static String statusMark(GoalResponseStatus status) {
        return switch (status) {
            case COMPLETED -> "[已完成]";
            case PARTIAL -> "[部分完成]";
            case WAITING_USER -> "[等待用户]";
            case SKIPPED -> "[已跳过]";
            case FAILED -> "[未完成]";
        };
    }

    private static String kindMark(GroundedResponseFact.Kind kind) {
        return switch (kind) {
            case EXTERNAL_FACT -> "[外部事实]";
            case INFERENCE -> "[推断]";
            case STATE_CHANGE -> "[状态变更]";
        };
    }
}
