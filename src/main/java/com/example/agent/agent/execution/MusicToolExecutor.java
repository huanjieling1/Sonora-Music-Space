package com.example.agent.agent.execution;

import com.example.agent.agent.capability.AgentRole;
import com.example.agent.agent.capability.AgentToolAuthorizer;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.tools.AgentActionContext;
import com.example.agent.tools.MusicAgentTools;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Sole adapter from execution strategies to the authorized MusicAgentTools surface. */
@Component
public final class MusicToolExecutor {
    private static final Pattern NUMBERED_TRACK = Pattern.compile("第([一二三四五六七八九十\\d]+)首");
    private static final Pattern NUMBERED_PAGE = Pattern.compile("第([一二三四五六七八九十\\d]+)页");

    private final MusicAgentTools tools;
    private final AgentActionContext actionContext;
    private final MusicAgentSessionStore sessionStore;
    private final AgentToolAuthorizer toolAuthorizer;

    public MusicToolExecutor(MusicAgentTools tools, AgentActionContext actionContext,
                             MusicAgentSessionStore sessionStore, AgentToolAuthorizer toolAuthorizer) {
        this.tools = tools;
        this.actionContext = actionContext;
        this.sessionStore = sessionStore;
        this.toolAuthorizer = toolAuthorizer;
    }

    public MusicExecutionResult randomPlaylist(MusicAgentRoute route) {
        String answer = invoke("playRandomQqPublicPlaylist", tools::playRandomQqPublicPlaylist);
        Set<AgentActionType> evidence = evidenceTypes();
        boolean queueLoaded = evidence.contains(AgentActionType.SHOW_MUSIC_RESULTS)
                && evidence.contains(AgentActionType.QUEUE_MUSIC_RESULTS);
        if (queueLoaded && !evidence.contains(AgentActionType.PLAY_TRACK)) {
            return MusicExecutionResult.partial(route,
                    com.example.agent.agent.intent.MusicIntentAgent.failureAnswer(answer))
                    .withEvidence(evidence);
        }
        return simple(route, answer);
    }

    public MusicExecutionResult searchPlaylists(MusicAgentTurn turn, MusicAgentRoute route) {
        return simple(route, invoke("searchQqPlaylists", () -> tools.searchQqPlaylists(turn.request())));
    }

    public MusicExecutionResult lookupArtist(MusicAgentTurn turn, MusicAgentRoute route) {
        return simple(route, invoke("searchQqArtists", () -> tools.searchQqArtists(turn.request())));
    }

    public MusicExecutionResult discoverTrends(MusicAgentTurn turn, MusicAgentRoute route) {
        return simple(route, invoke("queryQqMusicTrends", () -> tools.queryQqMusicTrends(turn.request())));
    }

    public MusicExecutionResult playResult(MusicAgentTurn turn, MusicAgentRoute route) {
        return simple(route, invoke("playRecommendedTrack",
                () -> tools.playRecommendedTrack(trackPosition(turn.request()))));
    }

    public MusicExecutionResult navigateResults(MusicAgentTurn turn, MusicAgentRoute route) {
        return simple(route, invoke("loadMusicResultsPage", () -> tools.loadMusicResultsPage(page(turn))));
    }

    public MusicExecutionResult queueResults(MusicAgentRoute route) {
        return simple(route, invoke("queueLatestRecommendations", tools::queueLatestRecommendations));
    }

    public MusicExecutionResult discover(MusicAgentTurn turn, UserTasteContext tasteContext) {
        String toolResult = invoke("recommendMusic",
                () -> tools.recommendMusic(turn.request(), tasteContext, turn.refreshBatch()));
        MusicRecommendationBo recommendation;
        try {
            recommendation = actionContext.actions().stream()
                    .filter(action -> action.recommendation() != null)
                    .reduce((left, right) -> right)
                    .map(action -> action.recommendation()).orElse(null);
        } catch (IllegalStateException ignored) {
            recommendation = null;
        }
        if (recommendation == null) {
            return evidence(new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, false,
                    com.example.agent.agent.intent.MusicIntentAgent.failureAnswer(toolResult)));
        }
        if (recommendation.tracks().isEmpty()) {
            String answer = StringUtils.hasText(recommendation.explanation())
                    ? recommendation.explanation()
                    : "没有找到可靠匹配的可播放歌曲，请补充作品名、歌手或场景关键词。";
            return evidence(new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, false, answer));
        }
        if (com.example.agent.agent.intent.MusicIntentAgent.wantsPlayback(turn.request())) {
            invoke("playRecommendedTrack", () -> tools.playRecommendedTrack(1));
            var first = recommendation.tracks().get(0);
            String artists = first.artists() == null || first.artists().isEmpty()
                    ? "未知歌手" : String.join(" / ", first.artists());
            return evidence(new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, true,
                    "已按你的当前要求搜索真实曲库，并开始播放第一首匹配结果《"
                            + first.name() + "》— " + artists + "。其他结果已显示在下方卡片中。"));
        }
        return evidence(new MusicExecutionResult(MusicAgentRoute.MUSIC_DISCOVERY, true,
                "已按你的当前要求搜索真实曲库，匹配结果已显示在下方卡片中。"));
    }

    private MusicExecutionResult simple(MusicAgentRoute route, String answer) {
        String safe = answer == null ? "" : answer.strip();
        boolean failed = safe.contains("失败") || safe.contains("不可用") || safe.contains("没有")
                || safe.startsWith("There are no") || safe.startsWith("Music catalog request failed");
        return evidence(new MusicExecutionResult(route, !failed,
                com.example.agent.agent.intent.MusicIntentAgent.failureAnswer(safe)));
    }

    private MusicExecutionResult evidence(MusicExecutionResult result) {
        return result.withEvidence(evidenceTypes());
    }

    private Set<AgentActionType> evidenceTypes() {
        try {
            return actionContext.actions().stream().map(action -> action.type())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalStateException ignored) {
            return Set.of();
        }
    }

    private int page(MusicAgentTurn turn) {
        Matcher matcher = NUMBERED_PAGE.matcher(turn.request());
        if (matcher.find()) return bounded(number(matcher.group(1)), 1, 20);
        int current = sessionStore.get(turn.memoryId()).map(MusicRecommendationBo::page).orElse(1);
        if (turn.request().contains("上一页")) return Math.max(1, current - 1);
        return Math.min(20, current + 1);
    }

    private static int trackPosition(String request) {
        Matcher matcher = NUMBERED_TRACK.matcher(request);
        return matcher.find() ? bounded(number(matcher.group(1)), 1, 10) : 1;
    }

    private static int number(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return switch (value) {
                case "一" -> 1; case "二" -> 2; case "三" -> 3; case "四" -> 4; case "五" -> 5;
                case "六" -> 6; case "七" -> 7; case "八" -> 8; case "九" -> 9; case "十" -> 10;
                default -> 1;
            };
        }
    }

    private static int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String invoke(String toolName, java.util.function.Supplier<String> invocation) {
        toolAuthorizer.requireAllowed(AgentRole.EXECUTION, toolName);
        return invocation.get();
    }
}
