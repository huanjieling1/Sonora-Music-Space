package com.example.agent.agent.feedback;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicFollowUpOutcome;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicFeedbackAction;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.dto.music.MusicFeedbackRequest;
import com.example.agent.model.dto.music.MusicPreferenceRequest;
import com.example.agent.service.MusicFeedbackService;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.stream.Collectors;

/** Applies only validated, user-explicit feedback and preference changes. */
@Component
public class MusicRecommendationFollowUpAgent {
    private static final Logger log = LoggerFactory.getLogger(MusicRecommendationFollowUpAgent.class);

    private final MusicAgentSessionStore sessionStore;
    private final MusicFeedbackService feedbackService;
    private final MusicPersonalizationService personalizationService;

    public MusicRecommendationFollowUpAgent(MusicAgentSessionStore sessionStore,
                                            MusicFeedbackService feedbackService,
                                            MusicPersonalizationService personalizationService) {
        this.sessionStore = sessionStore;
        this.feedbackService = feedbackService;
        this.personalizationService = personalizationService;
    }

    public MusicFollowUpOutcome apply(MusicAgentTurn turn, MusicTurnPlan plan) {
        Optional<MusicRecommendationBo> latest = sessionStore.get(turn.memoryId());
        if (plan.rejectLatestBatch() && latest.isEmpty()) {
            return new MusicFollowUpOutcome(false, "", StringUtils.hasText(plan.clarificationQuestion())
                    ? plan.clarificationQuestion() : "我还找不到你说的上一批结果，请告诉我想换掉哪些歌。", 0);
        }

        int rejected = plan.rejectLatestBatch() ? rejectLatest(turn, latest.orElseThrow()) : 0;
        for (MusicPreferenceChange preference : plan.preferences()) {
            if (preference.persistent()) {
                personalizationService.addPreference(turn.userId(), new MusicPreferenceRequest(
                        preference.type(), preference.value(), preference.polarity()));
            }
        }

        String preferenceText = plan.preferences().stream()
                .filter(MusicPreferenceChange::persistent)
                .map(value -> (value.polarity() > 0 ? "更喜欢 " : "希望避开 ") + value.value())
                .collect(Collectors.joining("，"));
        StringBuilder acknowledgment = new StringBuilder();
        if (StringUtils.hasText(preferenceText)) acknowledgment.append("已记住你").append(preferenceText).append("。");
        if (plan.rejectLatestBatch()) {
            if (rejected > 0) acknowledgment.append("刚才这批结果会在本次会话中降权避开。");
            else acknowledgment.append("刚才这批结果未能写入反馈，但新偏好仍会用于这次推荐。");
        }
        if (plan.refreshBatch() && !plan.rejectLatestBatch() && acknowledgment.isEmpty()) {
            acknowledgment.append("会保留当前口味条件，并优先换成这次会话里还没展示过的歌曲。");
        }
        if (acknowledgment.isEmpty()) acknowledgment.append("已理解你对刚才推荐的调整。" );
        return new MusicFollowUpOutcome(plan.recommendAgain(), plan.recommendationRequest(),
                plan.refreshBatch(), acknowledgment.toString(), rejected);
    }

    private int rejectLatest(MusicAgentTurn turn, MusicRecommendationBo recommendation) {
        String resolvedEntity = resolvedEntity(recommendation);
        int accepted = 0;
        for (var track : recommendation.tracks()) {
            try {
                feedbackService.record(turn.userId(), new MusicFeedbackRequest(
                        recommendation.searchId(), turn.conversationId(), MusicFeedbackAction.NOT_RELEVANT,
                        "用户通过自然语言拒绝最近推荐批次", track.id(), resolvedEntity,
                        null, (MusicEntityType) null));
                accepted++;
            } catch (RuntimeException exception) {
                log.debug("Could not attach batch feedback to track {}: {}", track.id(), exception.getMessage());
            }
        }
        return accepted;
    }

    private static String resolvedEntity(MusicRecommendationBo recommendation) {
        if (recommendation.understanding() != null && recommendation.understanding().resolved()) {
            return recommendation.understanding().canonicalName();
        }
        if (StringUtils.hasText(recommendation.searchQuery())) return recommendation.searchQuery();
        if (StringUtils.hasText(recommendation.description())) return recommendation.description();
        return "最近推荐";
    }
}
