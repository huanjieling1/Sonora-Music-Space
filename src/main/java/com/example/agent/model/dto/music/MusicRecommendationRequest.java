package com.example.agent.model.dto.music;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MusicRecommendationRequest(
        @NotNull(message = "会话标识不能为空")
        UUID conversationId,

        @NotBlank(message = "请描述你想听的音乐")
        @Size(max = 500, message = "音乐描述不能超过500个字符")
        String description,

        @Min(value = 1, message = "页码不能小于1")
        @Max(value = 20, message = "页码不能超过20")
        Integer page,

        @Min(value = 1, message = "每页数量不能少于1首")
        @Max(value = 10, message = "每页数量不能超过10首")
        Integer pageSize,

        @Min(value = 1, message = "推荐数量不能少于1首")
        @Max(value = 10, message = "推荐数量不能超过10首")
        Integer limit
) {
    public MusicRecommendationRequest(String description, Integer page, Integer pageSize, Integer limit) {
        this(new UUID(0L, 0L), description, page, pageSize, limit);
    }

    public int resolvedPage() {
        return page == null ? 1 : page;
    }

    public int resolvedPageSize() {
        if (pageSize != null) {
            return pageSize;
        }
        return limit == null ? 10 : limit;
    }
}
