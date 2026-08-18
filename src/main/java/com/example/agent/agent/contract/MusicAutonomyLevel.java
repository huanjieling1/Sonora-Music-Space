package com.example.agent.agent.contract;

/** Whether a proactive capability may run before the user explicitly confirms it. */
public enum MusicAutonomyLevel {
    READ_ONLY,
    CONFIRM_REQUIRED,
    DISABLED
}
