package com.example.agent.agent.planner;

import com.example.agent.agent.capability.AgentCapabilityRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, tool-name-free snapshot consumed by one planning attempt. */
public record CapabilityRegistrySnapshot(String schemaVersion, List<PlannerCapability> capabilities) {
    public CapabilityRegistrySnapshot {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "1.0" : schemaVersion.strip();
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        LinkedHashMap<String, PlannerCapability> unique = new LinkedHashMap<>();
        for (PlannerCapability capability : capabilities) {
            if (capability == null) throw new IllegalArgumentException("规划能力快照不能包含空值");
            if (unique.putIfAbsent(capability.id(), capability) != null) {
                throw new IllegalArgumentException("规划能力快照存在重复标识：" + capability.id());
            }
        }
        capabilities = List.copyOf(unique.values());
    }

    public static CapabilityRegistrySnapshot from(AgentCapabilityRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("Capability Registry 不能为空");
        List<PlannerCapability> projected = registry.planningCapabilities().stream()
                .peek(capability -> {
                    if (registry.toolNames().contains(capability.id())) {
                        throw new IllegalStateException("规划能力标识不能是工具实现名称：" + capability.id());
                    }
                })
                .map(PlannerCapability::from).toList();
        return new CapabilityRegistrySnapshot("1.0", projected);
    }

    public Map<String, PlannerCapability> byId() {
        LinkedHashMap<String, PlannerCapability> result = new LinkedHashMap<>();
        capabilities.forEach(value -> result.put(value.id(), value));
        return Map.copyOf(result);
    }
}
