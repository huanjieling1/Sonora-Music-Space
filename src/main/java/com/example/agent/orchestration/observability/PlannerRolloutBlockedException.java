package com.example.agent.orchestration.observability;

/** Signals the caller to keep or restore the legacy route without executing the dynamic plan. */
public final class PlannerRolloutBlockedException extends RuntimeException {
    private final PlannerRolloutPolicy.Decision decision;

    public PlannerRolloutBlockedException(PlannerRolloutPolicy.Decision decision) {
        super("动态计划未获准执行：" + decision.reason() + " (" + decision.action() + ")");
        this.decision = decision;
    }

    public PlannerRolloutPolicy.Decision decision() { return decision; }
}
