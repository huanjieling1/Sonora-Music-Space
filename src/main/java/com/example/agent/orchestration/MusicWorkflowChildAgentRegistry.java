package com.example.agent.orchestration;

import com.example.agent.agent.main.MusicWorkflowChildAgent;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Discovers executable child agents and resolves them without central route switch statements. */
@Component
public final class MusicWorkflowChildAgentRegistry {
    private final List<MusicWorkflowChildAgent> agents;

    public MusicWorkflowChildAgentRegistry(List<MusicWorkflowChildAgent> discovered) {
        List<MusicWorkflowChildAgent> values = discovered == null ? List.of() : discovered.stream()
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingInt((MusicWorkflowChildAgent value) -> value.descriptor().priority())
                        .reversed().thenComparing(value -> value.descriptor().id()))
                .toList();
        Set<String> ids = new HashSet<>();
        for (MusicWorkflowChildAgent agent : values) {
            if (!ids.add(agent.descriptor().id())) {
                throw new IllegalStateException("子 Agent 重复注册：" + agent.descriptor().id());
            }
        }
        this.agents = values;
    }

    public MusicWorkflowChildAgent require(String capabilityId) {
        List<MusicWorkflowChildAgent> matches = agents.stream()
                .filter(agent -> agent.descriptor().supports(capabilityId)).toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("没有子 Agent 能够执行能力：" + capabilityId);
        if (matches.size() > 1 && matches.get(0).descriptor().priority() == matches.get(1).descriptor().priority()) {
            throw new IllegalStateException("能力 " + capabilityId + " 存在同优先级子 Agent 冲突："
                    + matches.get(0).descriptor().id() + "、" + matches.get(1).descriptor().id());
        }
        return matches.get(0);
    }

    public List<MusicWorkflowChildAgent> agents() {
        return agents;
    }
}
