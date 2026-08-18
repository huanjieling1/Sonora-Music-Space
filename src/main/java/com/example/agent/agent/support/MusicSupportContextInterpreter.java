package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicSupportContext;

import java.util.Optional;

public interface MusicSupportContextInterpreter {
    Optional<MusicSupportContext> understand(String request);
}
