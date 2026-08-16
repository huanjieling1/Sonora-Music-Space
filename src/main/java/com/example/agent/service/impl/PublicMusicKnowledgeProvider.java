package com.example.agent.service.impl;

import java.util.Optional;

interface PublicMusicKnowledgeProvider {
    String id();

    boolean enabled();

    Optional<ExternalMusicEntity> lookup(String candidate);
}
