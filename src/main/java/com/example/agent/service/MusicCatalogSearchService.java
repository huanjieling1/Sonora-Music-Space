package com.example.agent.service;

import com.example.agent.model.bo.MusicCatalogSearchBo;
import com.example.agent.model.bo.MusicCatalogSearchType;

import java.util.UUID;

public interface MusicCatalogSearchService {
    MusicCatalogSearchBo search(long userId, UUID conversationId, String keyword,
                                MusicCatalogSearchType type, int page, int pageSize);
}
