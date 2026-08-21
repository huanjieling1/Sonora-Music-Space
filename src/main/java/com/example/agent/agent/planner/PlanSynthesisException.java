package com.example.agent.agent.planner;

/** Deterministic refusal when a bounded, registry-grounded plan cannot be produced. */
public final class PlanSynthesisException extends IllegalArgumentException {
    public PlanSynthesisException(String message) {
        super(message);
    }
}
