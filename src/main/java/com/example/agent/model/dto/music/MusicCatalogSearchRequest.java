package com.example.agent.model.dto.music;

import com.example.agent.model.bo.MusicCatalogSearchType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MusicCatalogSearchRequest(
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 160) String keyword,
        MusicCatalogSearchType type,
        @Min(1) @Max(20) Integer page,
        @Min(5) @Max(30) Integer pageSize
) {
    public MusicCatalogSearchType resolvedType() {
        return type == null ? MusicCatalogSearchType.ALL : type;
    }

    public int resolvedPage() {
        return page == null ? 1 : page;
    }

    public int resolvedPageSize() {
        return pageSize == null ? 20 : pageSize;
    }
}
