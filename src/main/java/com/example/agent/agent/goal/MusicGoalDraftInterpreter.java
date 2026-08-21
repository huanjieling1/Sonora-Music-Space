package com.example.agent.agent.goal;

import com.example.agent.agent.contract.planning.UserGoalGraph;

import java.util.Optional;

/** Optional semantic draft source. Deterministic decomposition remains the fallback and validation authority. */
public interface MusicGoalDraftInterpreter {
    Optional<UserGoalGraph> decompose(String request);
}
