package com.example.agent.service.impl;

import com.example.agent.config.MusicPersonalizationProperties;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicBehaviorEventType;
import com.example.agent.model.dto.music.MusicBehaviorEventRequest;
import com.example.agent.model.dto.music.MusicPreferenceRequest;
import com.example.agent.model.vo.music.MusicEventVo;
import com.example.agent.model.vo.music.MusicPolicyStatusVo;
import com.example.agent.model.vo.music.MusicPreferenceVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import com.example.agent.service.MusicPersonalizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MusicPersonalizationServiceImpl implements MusicPersonalizationService {
    private final MusicPersonalizationRepository repository;
    private final MusicPersonalizationProperties properties;
    private final MusicEmbeddingClient embeddings;
    private final Neo4jMusicGraphClient graph;
    private final MusicProfileSummaryBuilder summaryBuilder;

    public MusicPersonalizationServiceImpl(MusicPersonalizationRepository repository,
                                           MusicPersonalizationProperties properties,
                                           MusicEmbeddingClient embeddings,
                                           Neo4jMusicGraphClient graph,
                                           MusicProfileSummaryBuilder summaryBuilder) {
        this.repository = repository;
        this.properties = properties;
        this.embeddings = embeddings;
        this.graph = graph;
        this.summaryBuilder = summaryBuilder;
    }

    @Override
    public MusicEventVo recordEvent(long userId, MusicBehaviorEventRequest request) {
        var item = repository.findOwnedExposureItem(userId, request.searchId(), request.trackId())
                .orElseThrow(() -> new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "该歌曲不属于当前用户的这次推荐曝光"));
        validatePlaybackEvent(request.eventType(), request.playbackMs(), item.track().durationMs());
        var result = repository.recordEvent(userId, request.eventId(), request.searchId(), item,
                request.eventType(), request.playbackMs());
        return new MusicEventVo(true, result.duplicate(),
                result.duplicate() ? "该播放事件已经记录" : "播放行为已安全记录");
    }

    @Override
    public boolean isTrackLiked(long userId, UUID searchId, String trackId) {
        return repository.isTrackLiked(userId, searchId, trackId);
    }

    @Override
    public boolean isTrackSaved(long userId, UUID searchId, String trackId) {
        return repository.isTrackSaved(userId, searchId, trackId);
    }

    @Override
    public MusicProfileVo profile(long userId) {
        List<MusicPreferenceVo> all = repository.profile(userId).stream().map(this::toVo).toList();
        var stats = repository.profileStats(userId);
        List<MusicPreferenceVo> explicit = all.stream().filter(item -> "L1".equals(item.layer())).toList();
        List<MusicPreferenceVo> inferred = all.stream().filter(item -> "L2".equals(item.layer())).toList();
        return new MusicProfileVo(
                explicit, inferred, stats.labeledEvents(), stats.exposures(),
                summaryBuilder.build(all, stats.labeledEvents(), stats.exposures()));
    }

    @Override
    public MusicPreferenceVo addPreference(long userId, MusicPreferenceRequest request) {
        return toVo(repository.addExplicitPreference(userId, request.type(), request.value(), request.polarity()));
    }

    @Override
    public void deletePreference(long userId, UUID preferenceId) {
        if (!repository.deletePreference(userId, preferenceId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "偏好不存在或不属于当前用户");
        }
    }

    @Override
    public int clearLearned(long userId) {
        return repository.clearLearned(userId);
    }

    @Override
    public MusicPolicyStatusVo policyStatus(long userId) {
        var policy = repository.policy(properties.activePolicyVersion())
                .orElseThrow(() -> new IllegalStateException("Baseline music ranking policy is missing"));
        var stats = repository.profileStats(userId);
        return new MusicPolicyStatusVo(policy.version(), policy.status(), stats.labeledEvents(),
                stats.exposures(), properties.enabled(), embeddings.configured(), graph.ready());
    }

    private static void validatePlaybackEvent(MusicBehaviorEventType type, Long playbackMs, long durationMs) {
        if (type == MusicBehaviorEventType.SKIP) {
            if (playbackMs == null || playbackMs < 2_000) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "播放不足 2 秒或播放失败不能记为跳过");
            }
            long threshold = durationMs > 0 ? Math.min(30_000, Math.max(2_000, durationMs / 4)) : 30_000;
            if (playbackMs >= threshold) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "达到有效播放时长后不能记为快速跳过");
            }
        }
        if (type == MusicBehaviorEventType.COMPLETE) {
            long threshold = durationMs > 0 ? Math.round(durationMs * 0.9) : 30_000;
            if (playbackMs == null || playbackMs < threshold) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "歌曲未达到 90% 播放进度，不能记为播完");
            }
        }
    }

    private MusicPreferenceVo toVo(MusicPersonalizationRepository.PreferenceRow row) {
        return new MusicPreferenceVo(row.id(), row.layer(), row.scopeType(), row.type(), row.value(),
                row.polarity(), row.confidence(), row.evidenceCount(), row.source(), row.expiresAt());
    }
}
