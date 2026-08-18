package com.example.agent.agent.support;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicProactiveSuggestion;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.skill.AgentSkillDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublicPlaylistSupportAdapter implements MusicSupportCapabilityAdapter {
    @Override
    public boolean supports(AgentSkillDefinition skill) {
        return skill.tools().contains("searchQqPlaylists");
    }

    @Override
    public MusicAgentRoute executionRoute() {
        return MusicAgentRoute.PLAYLIST_SEARCH;
    }

    @Override
    public String executionRequest(MusicSupportContext context) {
        return "查找适合当前状态的 QQ 音乐公开歌单，方向是：" + context.musicDirection() + "。";
    }

    @Override
    public List<MusicProactiveSuggestion> followUps(MusicSupportContext context, AgentSkillDefinition skill) {
        String prompt = switch (context.goal()) {
            case ENERGIZE -> "找一些能慢慢提振状态、有力量但不过度吵闹的 QQ 音乐公开歌单。";
            case SOOTHE -> "找一些温柔治愈、适合慢慢平静下来的 QQ 音乐公开歌单。";
            default -> "找一些温暖、有陪伴感的 QQ 音乐公开歌单。";
        };
        return List.of(new MusicProactiveSuggestion("找一份陪伴歌单", prompt, skill.id(), false));
    }
}
