package com.example.agent.orchestration.dag;

@FunctionalInterface
public interface GenericDagTaskExecutor {
    DagTaskOutcome execute(DagTaskExecutionRequest request) throws Exception;
}
