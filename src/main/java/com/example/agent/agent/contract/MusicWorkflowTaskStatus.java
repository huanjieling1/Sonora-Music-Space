package com.example.agent.agent.contract;

public enum MusicWorkflowTaskStatus {
    PENDING,
    RUNNING,
    VERIFYING,
    RETRYING,
    WAITING_USER,
    COMPLETED,
    FAILED,
    SKIPPED
}
