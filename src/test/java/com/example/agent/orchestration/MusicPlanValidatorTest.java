package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicPlanValidatorTest {
    private final MusicPlanValidator validator = new MusicPlanValidator();

    @Test
    void rejectsCyclicTaskGraphBeforeAnyChildAgentRuns() {
        var first = new MusicWorkflowTaskSpec("a", "A", "intent-analysis", "Intent Agent",
                List.of("b"), 1);
        var second = new MusicWorkflowTaskSpec("b", "B", "result-verification", "Evaluator",
                List.of("a"), 1);
        var plan = new MusicWorkflowPlan(UUID.randomUUID(), "循环计划", MusicAgentRoute.CONVERSATION,
                List.of(first, second), 0);

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("循环依赖");
    }
}
