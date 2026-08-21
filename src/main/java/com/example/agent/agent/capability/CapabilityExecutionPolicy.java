package com.example.agent.agent.capability;

/** Bounded runtime budget and retry semantics advertised to the planner/compiler. */
public record CapabilityExecutionPolicy(
        int timeoutSeconds,
        int estimatedCostUnits,
        int maxAttempts,
        RetryStrategy retryStrategy,
        boolean idempotent
) {
    public CapabilityExecutionPolicy {
        if (timeoutSeconds < 1 || timeoutSeconds > 300) {
            throw new IllegalArgumentException("能力超时必须在 1 到 300 秒之间");
        }
        if (estimatedCostUnits < 0 || estimatedCostUnits > 1000) {
            throw new IllegalArgumentException("能力成本必须在 0 到 1000 之间");
        }
        if (maxAttempts < 1 || maxAttempts > 3) {
            throw new IllegalArgumentException("能力尝试次数必须在 1 到 3 之间");
        }
        retryStrategy = retryStrategy == null ? RetryStrategy.NONE : retryStrategy;
        if (retryStrategy == RetryStrategy.NONE && maxAttempts != 1) {
            throw new IllegalArgumentException("不允许重试的能力最大尝试次数必须为 1");
        }
        if (!idempotent && maxAttempts > 1) {
            throw new IllegalArgumentException("非幂等能力不能自动重试");
        }
    }

    public enum RetryStrategy {
        NONE,
        TRANSIENT_ONLY,
        SAFE_IDEMPOTENT
    }

    public static CapabilityExecutionPolicy readOnly(int timeoutSeconds, int cost, int maxAttempts) {
        return new CapabilityExecutionPolicy(timeoutSeconds, cost, maxAttempts,
                maxAttempts == 1 ? RetryStrategy.NONE : RetryStrategy.TRANSIENT_ONLY, true);
    }

    public static CapabilityExecutionPolicy mutation(int timeoutSeconds, int cost) {
        return new CapabilityExecutionPolicy(timeoutSeconds, cost, 1, RetryStrategy.NONE, false);
    }
}
