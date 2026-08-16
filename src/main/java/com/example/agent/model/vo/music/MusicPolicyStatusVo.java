package com.example.agent.model.vo.music;

public record MusicPolicyStatusVo(String activeVersion, String status, int labeledEvents,
                                  int exposures, boolean personalizationEnabled,
                                  boolean embeddingReady, boolean graphReady) {
}
