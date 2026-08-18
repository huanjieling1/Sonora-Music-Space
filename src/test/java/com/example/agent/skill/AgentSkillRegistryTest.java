package com.example.agent.skill;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSkillRegistryTest {
    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "summarizeMusicProfile",
            "recommendMusic",
            "searchQqPlaylists",
            "searchQqArtists",
            "queryQqMusicTrends",
            "playRandomQqPublicPlaylist",
            "playRecommendedTrack",
            "queueLatestRecommendations",
            "loadMusicResultsPage");

    @Test
    void loadsGoalLevelSkillsAndCoversEveryRegisteredTool() {
        AgentSkillRegistry registry = new AgentSkillRegistry();

        assertThat(registry.skills()).extracting(AgentSkillDefinition::id)
                .containsExactly(
                        "qq-music-trends",
                        "qq-artist-discovery",
                        "qq-public-playlists",
                        "recommendation-follow-up",
                        "music-discovery",
                        "music-playback",
                        "music-profile-insight");
        assertThat(registry.registeredToolNames()).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);
        assertThat(registry.coveredToolNames()).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);
    }

    @Test
    void rendersSkillSelectionAndToolWhitelistsIntoSystemInstructions() {
        AgentSkillRegistry registry = new AgentSkillRegistry();

        String prompt = registry.augmentSystemMessage("Base role");

        assertThat(prompt)
                .startsWith("Base role")
                .contains("# 当前可用 Skill 目录")
                .contains("## Skill：music-discovery")
                .contains("## Skill：recommendation-follow-up")
                .contains("先记录反馈与偏好，再调用一次 `recommendMusic`")
                .contains("名称：音乐发现")
                .contains("允许工具：recommendMusic, loadMusicResultsPage")
                .contains("允许工具：searchQqPlaylists, playRandomQqPublicPlaylist")
                .contains("允许工具：searchQqArtists")
                .contains("允许工具：queryQqMusicTrends")
                .contains("不得凭常识、封面或歌曲名虚构奖项")
                .contains("## Skill：music-profile-insight")
                .contains("不得推荐或虚构歌曲");
    }

    @Test
    void rejectsUnknownToolsAndUncoveredRegisteredTools() {
        AgentSkillDefinition invalid = new AgentSkillDefinition(
                "invalid-skill", "invalid-skill", "Invalid test skill", 1,
                Set.of("missingTool"), Set.of("invalid"), "Do nothing.", "test");

        assertThatThrownBy(() -> new AgentSkillRegistry(java.util.List.of(invalid), Set.of("realTool")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown tools");
    }

    @Test
    void acceptsNewVerifiedSkillWithoutEditingACentralCapabilityCatalog() {
        AgentSkillDefinition added = new AgentSkillDefinition(
                "weather", "天气查询", "查询实时天气", 1, Set.of("getWeather"),
                Set.of("天气", "气温"), "调用真实天气工具。", "test");

        AgentSkillRegistry registry = new AgentSkillRegistry(java.util.List.of(added), Set.of("getWeather"));

        assertThat(registry.skills()).containsExactly(added);
        assertThat(registry.coveredToolNames()).containsExactly("getWeather");
    }

    @Test
    void acceptsOnlyStandardSkillMetadata() {
        String invalid = """
                ---
                name: invalid
                description: Invalid skill
                tools: doSomething
                ---
                Run it.
                """;

        assertThatThrownBy(() -> AgentSkillRegistry.parseSkillDocument(invalid, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only name and description");
    }
}
