package com.example.agent.agent.capability;

public record AgentScopeDecision(AgentScopeType type, String reason) {
    public AgentScopeDecision {
        if (type == null) throw new IllegalArgumentException("范围判断不能为空");
        reason = reason == null ? "" : reason.strip();
    }
}
