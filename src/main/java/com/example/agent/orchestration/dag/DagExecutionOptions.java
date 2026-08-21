package com.example.agent.orchestration.dag;

/** Hard scheduler limits for one workflow run. */
public record DagExecutionOptions(int maxConcurrency, int workflowTimeoutSeconds,
                                  int taskTimeoutCapSeconds) {
    public DagExecutionOptions(int maxConcurrency, int workflowTimeoutSeconds) {
        this(maxConcurrency, workflowTimeoutSeconds, 60);
    }

    public DagExecutionOptions {
        if (maxConcurrency < 1 || maxConcurrency > 16) {
            throw new IllegalArgumentException("DAG 最大并发量必须在 1 到 16 之间");
        }
        if (workflowTimeoutSeconds < 1 || workflowTimeoutSeconds > 3600) {
            throw new IllegalArgumentException("DAG 工作流超时必须在 1 到 3600 秒之间");
        }
        if (taskTimeoutCapSeconds < 1 || taskTimeoutCapSeconds > 300) {
            throw new IllegalArgumentException("DAG 单任务超时上限必须在 1 到 300 秒之间");
        }
    }

    public static DagExecutionOptions defaults() {
        return new DagExecutionOptions(4, 300, 60);
    }
}
