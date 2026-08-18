package com.example.agent.model.dto.music;

import com.example.agent.model.bo.MusicBehaviorEventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MusicBehaviorEventRequest(
        @NotNull UUID eventId,
        UUID playbackSessionId,
        @NotNull UUID searchId,
        @NotBlank @Size(max = 255) String trackId,
        @NotNull MusicBehaviorEventType eventType,
        @Min(0) Long playbackMs,
        @Min(0) Long listenedMs
) {
    public MusicBehaviorEventRequest(UUID eventId, UUID searchId, String trackId,
                                     MusicBehaviorEventType eventType, Long playbackMs) {
        this(eventId, null, searchId, trackId, eventType, playbackMs, playbackMs);
    }
}
