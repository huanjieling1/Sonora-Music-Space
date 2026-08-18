package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicIntentDraft;

import java.util.Optional;

public interface MusicSemanticIntentInterpreter {
    Optional<MusicIntentDraft> understand(String request);
}
