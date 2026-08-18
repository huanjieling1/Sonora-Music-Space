package com.example.agent.model.bo;

import com.example.agent.agent.contract.MusicProactiveSuggestion;

import java.util.List;

/** UI-safe, capability-backed next actions shown after a proactive support turn. */
public record ProactiveSuggestionsBo(String title, List<MusicProactiveSuggestion> items) {
    public ProactiveSuggestionsBo {
        title = title == null || title.isBlank() ? "接下来想怎么听" : title.strip();
        items = items == null ? List.of() : items.stream().limit(4).toList();
    }
}
