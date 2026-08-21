package com.example.agent.orchestration.dag;

public enum DagTaskStatus {
    PENDING, READY, RUNNING, RETRYING, WAITING_USER, COMPLETED, FAILED, SKIPPED, CANCELLED
}
