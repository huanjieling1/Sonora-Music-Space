package com.example.agent.orchestration.replanning;

import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.CapabilitySideEffect;
import com.example.agent.agent.contract.planning.AcceptanceCriterion;
import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.TypedTaskResult;
import com.example.agent.agent.evaluation.EvaluationDecision;
import com.example.agent.orchestration.dag.DagTaskState;
import com.example.agent.orchestration.dag.DagTaskStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Replaces only a failed task and its downstream subgraph while freezing accepted results.
 * Every proposal is bounded by the plan budget, original acceptance contract and plan fingerprint.
 */
@Component
public final class BoundedReplanner {
    private final AgentCapabilityRegistry registry;
    private final ObjectMapper canonicalMapper;

    public BoundedReplanner(AgentCapabilityRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.canonicalMapper = objectMapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
    }

    public ReplanRequest prepare(UUID workflowId, CompiledPlan plan, String failedTaskId,
                                 String errorCode, String errorMessage, int replanAttempt,
                                 Map<String, DagTaskState> states, Map<String, Object> userInputs,
                                 Set<String> previousFingerprints,
                                 Set<String> replayApprovedTaskIds) {
        if (plan == null || failedTaskId == null || failedTaskId.isBlank()) {
            throw new IllegalArgumentException("重规划必须指定计划和失败任务");
        }
        Map<String, DagTaskState> taskStates = states == null ? Map.of() : Map.copyOf(states);
        Set<String> affected = failedSubgraph(plan, failedTaskId);
        LinkedHashMap<String, TypedTaskResult> preserved = new LinkedHashMap<>();
        for (PlanTask task : plan.tasks()) {
            DagTaskState state = taskStates.get(task.id());
            if (!affected.contains(task.id()) && state != null && state.status() == DagTaskStatus.COMPLETED
                    && state.result() != null && state.evaluation() != null
                    && state.evaluation().decision() == EvaluationDecision.PASS) {
                preserved.put(task.id(), state.result());
            }
        }
        LinkedHashMap<String, List<AcceptanceCriterion>> criteria = new LinkedHashMap<>();
        plan.tasks().stream().filter(task -> affected.contains(task.id()))
                .forEach(task -> criteria.put(task.id(), task.acceptanceCriteria()));
        return new ReplanRequest(workflowId, plan, failedTaskId, errorCode, errorMessage,
                replanAttempt, affected, preserved, criteria, taskStates,
                userInputs, previousFingerprints, replayApprovedTaskIds);
    }

    public ReplanResult replan(ReplanRequest request, SubgraphReplanStrategy strategy) {
        if (request == null || strategy == null) throw new IllegalArgumentException("重规划请求和策略不能为空");
        Set<String> preservedIds = request.preservedResults().keySet();
        if (request.replanAttempt() > request.currentPlan().maxReplans()) {
            return failureRoute(request, preservedIds, "已达到最大重规划次数");
        }
        String unsafeMutation = unsafeMutation(request);
        if (unsafeMutation != null) {
            return new ReplanResult(ReplanResult.Kind.ASK_USER, null, request.failedSubgraphTaskIds(),
                    preservedIds, "", "replan.replay." + unsafeMutation,
                    "副作用任务可能已经执行，必须由用户确认后才能重放：" + unsafeMutation);
        }

        ReplanProposal proposal;
        try {
            proposal = strategy.propose(request);
        } catch (Exception error) {
            return failureRoute(request, preservedIds, "重规划策略执行失败：" + safeMessage(error));
        }
        if (proposal == null) return failureRoute(request, preservedIds, "重规划策略没有返回方案");
        if (proposal.kind() == ReplanProposal.Kind.ASK_USER) {
            return new ReplanResult(ReplanResult.Kind.ASK_USER, null, request.failedSubgraphTaskIds(),
                    preservedIds, "", proposal.waitingSlot(), proposal.message());
        }
        if (proposal.kind() == ReplanProposal.Kind.FAIL) {
            return failureRoute(request, preservedIds, proposal.message().isBlank()
                    ? "重规划策略明确拒绝生成方案" : proposal.message());
        }

        String validationError = validateReplacement(request, proposal.replacementTasks());
        if (validationError != null) return failureRoute(request, preservedIds, validationError);
        String currentFingerprint = fingerprint(tasks(request.currentPlan(), request.failedSubgraphTaskIds()));
        String proposalFingerprint = fingerprint(proposal.replacementTasks());
        if (proposalFingerprint.equals(currentFingerprint)
                || request.previousPlanFingerprints().contains(proposalFingerprint)) {
            return failureRoute(request, preservedIds, "重规划生成了与已失败方案相同的子图");
        }

        CompiledPlan updated = replace(request.currentPlan(), request.failedSubgraphTaskIds(),
                proposal.replacementTasks());
        return new ReplanResult(ReplanResult.Kind.APPLIED, updated, request.failedSubgraphTaskIds(),
                preservedIds, proposalFingerprint, "", proposal.message());
    }

    public ReplanRecord record(ReplanRequest request, ReplanResult result) {
        return new ReplanRecord(request.replanAttempt(), request.failedTaskId(), request.errorCode(),
                result.replacedTaskIds(), result.preservedTaskIds(), result.planFingerprint(),
                result.kind(), result.message(), Instant.now());
    }

    public Set<String> failedSubgraph(CompiledPlan plan, String failedTaskId) {
        LinkedHashMap<String, List<String>> downstream = new LinkedHashMap<>();
        plan.tasks().forEach(task -> downstream.put(task.id(), new ArrayList<>()));
        if (!downstream.containsKey(failedTaskId)) throw new IllegalArgumentException("计划中不存在失败任务：" + failedTaskId);
        plan.tasks().forEach(task -> task.dependencies().forEach(dependency -> {
            List<String> values = downstream.get(dependency);
            if (values != null) values.add(task.id());
        }));
        LinkedHashSet<String> affected = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(failedTaskId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!affected.add(current)) continue;
            downstream.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        return Set.copyOf(affected);
    }

    public String fingerprint(List<PlanTask> tasks) {
        try {
            List<PlanTask> sorted = tasks.stream().sorted(Comparator.comparing(PlanTask::id)).toList();
            byte[] json = canonicalMapper.writeValueAsBytes(sorted);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("重规划任务无法序列化", error);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String unsafeMutation(ReplanRequest request) {
        for (String taskId : request.failedSubgraphTaskIds()) {
            DagTaskState state = request.taskStates().get(taskId);
            PlanTask task = request.currentPlan().tasks().stream().filter(value -> value.id().equals(taskId))
                    .findFirst().orElse(null);
            if (state == null || task == null || state.attempts() == 0
                    || request.replayApprovedTaskIds().contains(taskId)) continue;
            AgentCapabilityDefinition capability = registry.find(task.capabilityId()).orElse(null);
            if (capability != null && capability.sideEffect() != CapabilitySideEffect.READ_ONLY) return taskId;
        }
        return null;
    }

    private String validateReplacement(ReplanRequest request, List<PlanTask> replacements) {
        Set<String> replacementIds = replacements.stream().map(PlanTask::id)
                .collect(java.util.stream.Collectors.toSet());
        if (replacementIds.size() != replacements.size()) return "替换子图包含重复任务 ID";
        if (!replacementIds.equals(request.failedSubgraphTaskIds())) {
            return "替换子图必须保持失败子图的边界任务 ID";
        }
        Set<String> allIds = request.currentPlan().tasks().stream().map(PlanTask::id)
                .collect(java.util.stream.Collectors.toSet());
        for (PlanTask task : replacements) {
            AgentCapabilityDefinition capability = registry.find(task.capabilityId()).orElse(null);
            if (capability == null || !capability.plannerVisible()) return "替换任务使用了未注册能力：" + task.capabilityId();
            if (!task.acceptanceCriteria().containsAll(request.acceptanceCriteria()
                    .getOrDefault(task.id(), List.of()))) {
                return "替换任务丢失原验收条件：" + task.id();
            }
            if (task.dependencies().stream().anyMatch(dependency -> !allIds.contains(dependency))) {
                return "替换任务引用了不存在的依赖：" + task.id();
            }
        }
        LinkedHashMap<String, PlanTask> combined = new LinkedHashMap<>();
        request.currentPlan().tasks().stream().filter(task -> !replacementIds.contains(task.id()))
                .forEach(task -> combined.put(task.id(), task));
        replacements.forEach(task -> combined.put(task.id(), task));
        if (hasCycle(combined)) return "替换子图引入循环依赖";
        return null;
    }

    private static boolean hasCycle(Map<String, PlanTask> tasks) {
        HashSet<String> visiting = new HashSet<>();
        HashSet<String> visited = new HashSet<>();
        for (String id : tasks.keySet()) if (cycle(id, tasks, visiting, visited)) return true;
        return false;
    }

    private static boolean cycle(String id, Map<String, PlanTask> tasks,
                                 Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) return false;
        if (!visiting.add(id)) return true;
        PlanTask task = tasks.get(id);
        if (task != null) for (String dependency : task.dependencies()) {
            if (cycle(dependency, tasks, visiting, visited)) return true;
        }
        visiting.remove(id);
        visited.add(id);
        return false;
    }

    private static CompiledPlan replace(CompiledPlan current, Set<String> affected,
                                        List<PlanTask> replacements) {
        Map<String, PlanTask> replacementById = replacements.stream()
                .collect(java.util.stream.Collectors.toMap(PlanTask::id, value -> value));
        ArrayList<PlanTask> tasks = new ArrayList<>();
        for (PlanTask task : current.tasks()) {
            tasks.add(affected.contains(task.id()) ? replacementById.get(task.id()) : task);
        }
        return new CompiledPlan(current.schemaVersion(), UUID.randomUUID(), current.goalGraphId(),
                tasks, stages(tasks), current.maxReplans());
    }

    private static List<List<String>> stages(List<PlanTask> tasks) {
        LinkedHashMap<String, Integer> indegree = new LinkedHashMap<>();
        LinkedHashMap<String, List<String>> downstream = new LinkedHashMap<>();
        tasks.forEach(task -> {
            indegree.put(task.id(), task.dependencies().size());
            downstream.put(task.id(), new ArrayList<>());
        });
        tasks.forEach(task -> task.dependencies().forEach(dependency -> downstream.get(dependency).add(task.id())));
        ArrayList<List<String>> stages = new ArrayList<>();
        LinkedHashSet<String> remaining = new LinkedHashSet<>(indegree.keySet());
        while (!remaining.isEmpty()) {
            List<String> stage = remaining.stream().filter(id -> indegree.get(id) == 0).sorted().toList();
            if (stage.isEmpty()) throw new IllegalArgumentException("替换计划包含循环依赖");
            stages.add(stage);
            stage.forEach(id -> {
                remaining.remove(id);
                downstream.get(id).forEach(child -> indegree.computeIfPresent(child, (key, value) -> value - 1));
            });
        }
        return List.copyOf(stages);
    }

    private static List<PlanTask> tasks(CompiledPlan plan, Set<String> ids) {
        return plan.tasks().stream().filter(task -> ids.contains(task.id())).toList();
    }

    private static ReplanResult failureRoute(ReplanRequest request, Set<String> preservedIds, String message) {
        if (recoverableWithUser(request.errorCode())) {
            return new ReplanResult(ReplanResult.Kind.ASK_USER, null, request.failedSubgraphTaskIds(),
                    preservedIds, "", "replan.input." + request.failedTaskId(), message);
        }
        return new ReplanResult(ReplanResult.Kind.FAIL, null, request.failedSubgraphTaskIds(),
                preservedIds, "", "", message);
    }

    private static boolean recoverableWithUser(String code) {
        String value = code == null ? "" : code.toUpperCase(java.util.Locale.ROOT);
        return value.contains("INPUT") || value.contains("MISSING") || value.contains("ENTITY")
                || value.contains("NOT_FOUND") || value.contains("AMBIGUOUS");
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }
}
