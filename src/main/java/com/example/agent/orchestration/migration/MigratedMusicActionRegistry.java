package com.example.agent.orchestration.migration;

import com.example.agent.model.bo.AgentActionBo;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Collects trusted legacy UI actions emitted by parallel migrated capability tasks. */
@Component
public final class MigratedMusicActionRegistry {
    private final ConcurrentHashMap<UUID, LinkedHashMap<String, List<AgentActionBo>>> actions =
            new ConcurrentHashMap<>();

    public void record(UUID workflowId, String taskId, List<AgentActionBo> emitted) {
        if (workflowId == null || taskId == null || taskId.isBlank() || emitted == null || emitted.isEmpty()) return;
        LinkedHashMap<String, List<AgentActionBo>> bucket = actions.computeIfAbsent(workflowId,
                ignored -> new LinkedHashMap<>());
        synchronized (bucket) {
            bucket.put(taskId, List.copyOf(emitted));
        }
    }

    public List<AgentActionBo> drainAccepted(UUID workflowId, Set<String> acceptedTaskIds) {
        LinkedHashMap<String, List<AgentActionBo>> bucket = actions.remove(workflowId);
        if (bucket == null) return List.of();
        synchronized (bucket) {
            LinkedHashMap<UUID, AgentActionBo> trusted = new LinkedHashMap<>();
            bucket.entrySet().stream().filter(entry -> acceptedTaskIds.contains(entry.getKey()))
                    .flatMap(entry -> entry.getValue().stream())
                    .forEach(action -> trusted.putIfAbsent(action.id(), action));
            return List.copyOf(trusted.values());
        }
    }

    public void release(UUID workflowId) {
        if (workflowId != null) actions.remove(workflowId);
    }
}
