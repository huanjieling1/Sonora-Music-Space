package com.example.agent.service;

import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.MusicSearchTask;

import java.util.List;

public interface MusicCatalogProvider {
    String id();

    String displayName();

    boolean configured();

    boolean fallbackOnly();

    int order();

    List<String> playbackTypes();

    List<MusicTrackBo> search(String query, int limit);

    default List<MusicTrackBo> search(String query, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return search(query, Math.min(200, offset + pageSize)).stream()
                .skip(offset)
                .limit(pageSize)
                .toList();
    }

    default List<MusicTrackBo> search(MusicSearchTask task, int limit) {
        return search(task.query(), limit);
    }

    default List<MusicTrackBo> search(MusicSearchTask task, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return search(task, Math.min(200, offset + pageSize)).stream()
                .skip(offset)
                .limit(pageSize)
                .toList();
    }
}
