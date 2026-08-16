package com.example.agent.service.impl;

import com.example.agent.exception.AppException;
import com.example.agent.model.bo.MusicFeedbackAction;
import com.example.agent.model.dto.music.MusicFeedbackRequest;
import com.example.agent.model.vo.music.MusicFeedbackVo;
import com.example.agent.service.MusicFeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MusicFeedbackServiceImpl implements MusicFeedbackService {
    private final MusicKnowledgeRepository repository;
    private final MusicPersonalizationRepository personalizationRepository;

    public MusicFeedbackServiceImpl(MusicKnowledgeRepository repository,
                                    MusicPersonalizationRepository personalizationRepository) {
        this.repository = repository;
        this.personalizationRepository = personalizationRepository;
    }

    @Override
    public MusicFeedbackVo record(long userId, MusicFeedbackRequest request) {
        personalizationRepository.requireOwnedExposure(userId, request.searchId(), request.conversationId());
        if (request.action() == MusicFeedbackAction.NOT_RELEVANT
                && (!StringUtils.hasText(request.trackId())
                || !StringUtils.hasText(request.resolvedEntityName()))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "标记不相关结果时需要歌曲和当前识别实体");
        }
        if (request.action() == MusicFeedbackAction.CORRECT_ENTITY
                && (!StringUtils.hasText(request.correctedEntityName())
                || request.correctedEntityType() == null)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "更正理解时需要实体名称和类型");
        }
        if (request.action() == MusicFeedbackAction.NOT_RELEVANT) {
            var item = personalizationRepository.findOwnedExposureItem(userId, request.searchId(), request.trackId())
                    .orElseThrow(() -> new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "不相关反馈引用的歌曲不属于这次推荐"));
            if (!item.conversationId().equals(request.conversationId())) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "反馈会话与推荐曝光不一致");
            }
        }
        repository.saveFeedback(userId, request.conversationId().toString(), request.searchId().toString(),
                request.action().name(),
                request.description().strip(), request.trackId(), request.resolvedEntityName(),
                request.correctedEntityName(), request.correctedEntityType());
        String message = request.action() == MusicFeedbackAction.CORRECT_ENTITY
                ? "已记住新的实体理解，后续搜索将优先采用" : "已记住这首歌与当前实体不相关";
        return new MusicFeedbackVo(true, message);
    }
}
