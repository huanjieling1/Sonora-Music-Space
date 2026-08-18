package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicProactiveSuggestion;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.skill.AgentSkillDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MusicDiscoverySupportAdapter implements MusicSupportCapabilityAdapter {
    @Override
    public boolean supports(AgentSkillDefinition skill) {
        return skill.tools().contains("recommendMusic");
    }

    @Override
    public MusicAgentRoute executionRoute() {
        return MusicAgentRoute.MUSIC_DISCOVERY;
    }

    @Override
    public String executionRequest(MusicSupportContext context) {
        String direction = context.musicDirection().isBlank() ? defaultDirection(context.goal())
                : context.musicDirection();
        return "结合我的可靠音乐偏好，推荐一些" + direction + "的真实歌曲。";
    }

    @Override
    public List<MusicProactiveSuggestion> followUps(MusicSupportContext context, AgentSkillDefinition skill) {
        return switch (context.goal()) {
            case ENERGIZE -> List.of(
                    suggestion("再有力量一点", "推荐一些更有力量、节奏逐渐明亮的歌。", skill),
                    suggestion("保持轻松", "推荐一些轻松明亮、不过度亢奋的歌。", skill));
            case SOOTHE -> List.of(
                    suggestion("再安静一点", "推荐一些更安静、低刺激、适合慢慢平静下来的歌。", skill),
                    suggestion("给我一点力量", "推荐一些温柔但能慢慢带来力量的歌。", skill));
            default -> List.of(
                    suggestion("更温柔一点", "推荐一些温柔、有陪伴感的歌。", skill),
                    suggestion("换一种感觉", "换一批更轻松明亮的歌。", skill));
        };
    }

    @Override
    public int scoreBonus(MusicSupportContext context) {
        return 20;
    }

    private static MusicProactiveSuggestion suggestion(String label, String prompt,
                                                        AgentSkillDefinition skill) {
        return new MusicProactiveSuggestion(label, prompt, skill.id(), false);
    }

    private static String defaultDirection(MusicSupportContext.SupportGoal goal) {
        return switch (goal) {
            case SOOTHE -> "温柔舒缓、不过分悲伤";
            case ACCOMPANY -> "温暖、有陪伴感";
            case ENERGIZE -> "由平缓渐进到明亮、有力量";
            case DISTRACT -> "有新鲜感、能轻轻转移注意";
            case FOCUS -> "稳定、少打扰、适合专注";
            case EXPLORE -> "有探索感、不过度陌生";
            default -> "适合此刻状态";
        };
    }
}
