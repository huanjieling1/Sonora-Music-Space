package com.example.agent.model.ao;

import java.util.UUID;

public record MusicRecommendationAo(Long userId, UUID conversationId, String description, int page, int pageSize) {
    public static final int MAX_PAGE = 20;
    public static final int MAX_PAGE_SIZE = 10;

    public MusicRecommendationAo(String description, int limit) {
        this(null, null, description, 1, limit);
    }

    public MusicRecommendationAo(String description, int page, int pageSize) {
        this(null, null, description, page, pageSize);
    }

    public MusicRecommendationAo {
        if ((userId == null) != (conversationId == null)) {
            throw new IllegalArgumentException("用户与会话标识必须同时提供");
        }
        if (userId != null && userId <= 0) {
            throw new IllegalArgumentException("用户标识必须为正数");
        }
        if (page < 1 || page > MAX_PAGE) {
            throw new IllegalArgumentException("音乐页码必须在1到20之间");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("音乐分页大小必须在1到10之间");
        }
    }

    public boolean personalizedRequest() {
        return userId != null && conversationId != null;
    }
}
