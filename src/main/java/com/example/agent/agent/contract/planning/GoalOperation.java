package com.example.agent.agent.contract.planning;

/** Domain-level operation requested by the user; it is not a concrete tool name. */
public enum GoalOperation {
    RESOLVE,
    SEARCH,
    LOOKUP,
    RECOMMEND,
    ANALYZE,
    SUMMARIZE,
    CREATE,
    UPDATE,
    DELETE,
    PLAY,
    QUEUE_ADD,
    QUEUE_REMOVE,
    NAVIGATE,
    CONFIRM,
    RESPOND,
    UNKNOWN
}
