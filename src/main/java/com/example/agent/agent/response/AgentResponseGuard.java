package com.example.agent.agent.response;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.AgentActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Final non-model boundary that blocks unsupported capability and unverified action claims. */
@Component
public class AgentResponseGuard {
    private static final Logger log = LoggerFactory.getLogger(AgentResponseGuard.class);
    private static final Pattern GENERIC_AI_SELF_DESCRIPTION = Pattern.compile(
            "作为.{0,24}(?:人工智能|ai)助手|我具备多种能力|以下是我(?:的|所具备的).{0,12}能力|"
                    + "(?:我|sonora|本系统|这个助手).{0,12}(?:可以|能够|支持|具备).{0,80}(?:功能|能力)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PLAYBACK_SUCCESS_CLAIM = Pattern.compile(
            "已(?:经)?(?:开始)?播放|开始播放|正在播放|已经为你放", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUEUE_SUCCESS_CLAIM = Pattern.compile(
            "已(?:经)?(?:全部)?加入(?:播放)?队列|队列中已加入", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEARCH_SUCCESS_CLAIM = Pattern.compile(
            "已(?:经)?(?:为你)?(?:搜索到|找到)|匹配结果已(?:显示|生成)|已按.{0,30}搜索真实曲库",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UNVERIFIED_SONG_LIST = Pattern.compile(
            "(?m)^\\s*(?:[1-9]|10)[.、)]\\s*(?:《|\\*\\*)?.{1,80}(?:—|-).+");

    private final AgentCapabilityRegistry registry;

    public AgentResponseGuard(AgentCapabilityRegistry registry) {
        this.registry = registry;
    }

    public String enforce(MusicAgentRoute route, String answer, List<AgentActionBo> actions) {
        if (route == MusicAgentRoute.CAPABILITY_INQUIRY) return registry.capabilityAnswer();
        if (route == MusicAgentRoute.OUT_OF_SCOPE) return registry.outOfScopeAnswer();
        if (route == MusicAgentRoute.SCOPE_CLARIFICATION) {
            return StringUtils.hasText(answer) ? answer.strip() : registry.clarificationAnswer();
        }

        String safe = StringUtils.hasText(answer) ? answer.strip() : registry.unverifiedActionAnswer();
        List<AgentActionBo> evidence = actions == null ? List.of() : actions;
        Set<AgentActionType> actionTypes = evidence.stream().map(AgentActionBo::type).collect(java.util.stream.Collectors.toSet());

        if (GENERIC_AI_SELF_DESCRIPTION.matcher(safe).find()) {
            return blocked(route, "self-description bypassed runtime registry", registry.capabilityAnswer());
        }
        if (PLAYBACK_SUCCESS_CLAIM.matcher(safe).find() && !actionTypes.contains(AgentActionType.PLAY_TRACK)) {
            return blocked(route, "playback claim without PLAY_TRACK", registry.unverifiedActionAnswer());
        }
        if (QUEUE_SUCCESS_CLAIM.matcher(safe).find() && !actionTypes.contains(AgentActionType.QUEUE_MUSIC_RESULTS)) {
            return blocked(route, "queue claim without QUEUE_MUSIC_RESULTS", registry.unverifiedActionAnswer());
        }
        boolean hasSearchEvidence = actionTypes.contains(AgentActionType.SHOW_MUSIC_RESULTS)
                || actionTypes.contains(AgentActionType.SHOW_QQ_PLAYLIST_RESULTS)
                || actionTypes.contains(AgentActionType.SHOW_QQ_ARTIST_RESULTS)
                || actionTypes.contains(AgentActionType.SHOW_QQ_CHART_RESULTS);
        if (SEARCH_SUCCESS_CLAIM.matcher(safe).find() && !hasSearchEvidence) {
            return blocked(route, "search claim without structured results", registry.unverifiedActionAnswer());
        }
        if (route == MusicAgentRoute.CONVERSATION && evidence.isEmpty()
                && UNVERIFIED_SONG_LIST.matcher(safe).find()) {
            return blocked(route, "song list without verified catalog results",
                    "我不能在没有真实曲库结果时直接编写歌曲清单。请告诉我想听的歌手、曲风或场景，我会通过真实曲库搜索。" );
        }
        return safe;
    }

    private static String blocked(MusicAgentRoute route, String reason, String fallback) {
        log.warn("Agent response blocked route={} reason={}", route, reason);
        return fallback;
    }
}
