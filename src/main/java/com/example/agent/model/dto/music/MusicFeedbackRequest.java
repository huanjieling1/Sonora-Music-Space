package com.example.agent.model.dto.music;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicFeedbackAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MusicFeedbackRequest(
        @NotNull UUID searchId,
        @NotNull UUID conversationId,
        @NotNull MusicFeedbackAction action,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 255) String trackId,
        @Size(max = 160) String resolvedEntityName,
        @Size(max = 160) String correctedEntityName,
        MusicEntityType correctedEntityType
) {
}
