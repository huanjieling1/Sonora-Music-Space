package com.example.agent.agent.contract;

/** Closed response modes owned by the response child agent, not by the workflow scheduler. */
public enum MusicResponseTaskMode {
    VERIFIED_EXECUTION,
    SUPPORTIVE,
    SAFETY,
    CONVERSATION,
    EXISTING_TEXT
}
