package com.example.agent.service.impl;

import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.bo.MusicRecommendationBo;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Keeps only the latest result set needed by follow-up playback commands. */
@Component
public class MusicAgentSessionStore {
    private final ConcurrentMap<ConversationMemoryId, MusicRecommendationBo> latest = new ConcurrentHashMap<>();

    public void put(ConversationMemoryId memoryId, MusicRecommendationBo recommendation) {
        latest.put(memoryId, recommendation);
    }

    public Optional<MusicRecommendationBo> get(ConversationMemoryId memoryId) {
        return Optional.ofNullable(latest.get(memoryId));
    }
}
