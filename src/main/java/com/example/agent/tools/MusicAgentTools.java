package com.example.agent.tools;

import com.example.agent.exception.AppException;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.QqMusicService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.IntStream;

@Component
public class MusicAgentTools {
    private final MusicRecommendationService recommendationService;
    private final MusicPersonalizationService personalizationService;
    private final MusicAgentSessionStore sessionStore;
    private final AgentActionContext actionContext;
    private final QqMusicService qqMusicService;

    @Autowired
    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext,
                           QqMusicService qqMusicService) {
        this.recommendationService = recommendationService;
        this.personalizationService = personalizationService;
        this.sessionStore = sessionStore;
        this.actionContext = actionContext;
        this.qqMusicService = qqMusicService;
    }

    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext) {
        this(recommendationService, personalizationService, sessionStore, actionContext, null);
    }

    @Tool("""
            Analyze and summarize the current listener's stored music profile. Use this tool for requests such as
            "总结我的偏好", "分析我的音乐画像", "我喜欢什么", or "系统对我了解多少". Report only auditable
            explicit preferences, qualified behavioral inferences, evidence maturity, and limitations. This is not
            a catalog search: do not call recommendMusic unless the user separately asks for songs or a playlist.
            """)
    public String summarizeMusicProfile() {
        try {
            return profileSummary(personalizationService.profile(actionContext.memoryId().userId()).summary());
        } catch (AppException exception) {
            return "读取音乐画像失败：" + exception.getMessage();
        } catch (RuntimeException exception) {
            return "音乐画像暂时无法读取，请稍后重试。";
        }
    }

    @Tool("""
            Search Sonora's real music catalogs for a track, artist, album, similar music, genre, mood, activity,
            or scene. Use this tool for every music search or recommendation request. The description should retain
            all useful constraints from the current conversation. This tool displays page 1 with 10 tracks in the
            music panel; use loadMusicResultsPage for later pages.
            """)
    public String recommendMusic(
            @P("Complete music request resolved from the current conversation") String description) {
        if (!StringUtils.hasText(description)) {
            return "Music search was not run because the request description is empty.";
        }
        try {
            var memoryId = actionContext.memoryId();
            MusicRecommendationBo recommendation = recommendationService.recommend(
                    new MusicRecommendationAo(memoryId.userId(), memoryId.conversationId(), description.trim(),
                            1, MusicRecommendationAo.MAX_PAGE_SIZE));
            sessionStore.put(actionContext.memoryId(), recommendation);
            actionContext.add(AgentActionBo.showMusic(recommendation));
            return summarize(recommendation);
        } catch (AppException exception) {
            return "Music catalog request failed: " + exception.getMessage();
        } catch (RuntimeException exception) {
            return "Music catalog request failed temporarily. Ask the user to retry later.";
        }
    }

    @Tool("""
            Randomly choose a real public playlist created by a QQ Music user, load its real tracks, display the
            playlist in Sonora, replace the visible queue with a shuffled order, and start playing the first track.
            Use this tool when the listener explicitly asks to randomly play QQ Music homepage, popular public
            playlists, another user's playlist, or says "随机播放 QQ 音乐歌单". Do not use it for a named track.
            """)
    public String playRandomQqPublicPlaylist() {
        if (qqMusicService == null) return "QQ 音乐公开歌单工具当前不可用。";
        try {
            var candidates = new java.util.ArrayList<>(qqMusicService.publicPlaylists(1, 16));
            if (candidates.isEmpty()) return "QQ 音乐首页暂时没有返回可用的公开歌单。";
            java.util.Collections.shuffle(candidates);
            var memoryId = actionContext.memoryId();
            var playlist = qqMusicService.publicPlaylist(memoryId.userId(), memoryId.conversationId(),
                    candidates.get(0).id(), 60, true);
            if (playlist.tracks().isEmpty()) return "选中的 QQ 音乐公开歌单暂时没有可播放歌曲。";
            MusicRecommendationBo recommendation = new MusicRecommendationBo(
                    playlist.searchId(), "随机播放 QQ 音乐公开歌单", playlist.name(),
                    "来自 QQ 音乐用户“" + playlist.creatorName() + "”创建的公开歌单“" + playlist.name() + "”。",
                    com.example.agent.model.bo.MusicUnderstandingBo.unresolved(), List.of("QQ 音乐"),
                    playlist.tracks(), playlist.tracks().size(), 0, 1, playlist.tracks().size(),
                    false, 1, playlist.policyVersion(), playlist.personalizationStatus());
            sessionStore.put(memoryId, recommendation);
            actionContext.add(AgentActionBo.showMusic(recommendation));
            actionContext.add(AgentActionBo.queueMusic(recommendation));
            actionContext.add(AgentActionBo.playTrack(playlist.tracks().get(0)));
            return "已随机选择 QQ 音乐公开歌单《" + playlist.name() + "》（创建者："
                    + playlist.creatorName() + "），并随机排列 " + playlist.tracks().size() + " 首歌曲开始播放。";
        } catch (AppException exception) {
            return "QQ 音乐公开歌单加载失败：" + exception.getMessage();
        } catch (RuntimeException exception) {
            return "QQ 音乐公开歌单暂时无法加载，请稍后重试。";
        }
    }

    @Tool("""
            Play one track from the most recent Sonora music results. Call only when the user explicitly asks to play
            or start listening. Position is one-based: the first result is 1.
            """)
    public String playRecommendedTrack(@P("One-based track position in the latest results") int position) {
        MusicRecommendationBo recommendation = sessionStore.get(actionContext.memoryId()).orElse(null);
        if (recommendation == null || recommendation.tracks().isEmpty()) {
            return "There are no recent music results. Call recommendMusic first.";
        }
        if (position < 1 || position > recommendation.tracks().size()) {
            return "Track position is outside the latest result list. Choose 1 to "
                    + recommendation.tracks().size() + ".";
        }
        MusicTrackBo track = recommendation.tracks().get(position - 1);
        actionContext.add(AgentActionBo.playTrack(track));
        return "Playback requested for: " + track.name() + " — " + artists(track) + ".";
    }

    @Tool("Add all tracks from the most recent Sonora music results to the visible playback queue.")
    public String queueLatestRecommendations() {
        MusicRecommendationBo recommendation = sessionStore.get(actionContext.memoryId()).orElse(null);
        if (recommendation == null || recommendation.tracks().isEmpty()) {
            return "There are no recent music results. Call recommendMusic first.";
        }
        actionContext.add(AgentActionBo.queueMusic(recommendation));
        return recommendation.tracks().size() + " tracks were sent to the playback queue.";
    }

    @Tool("""
            Load another page for the most recent Sonora music search. Use for next page, previous page, or a
            specific page request. Page numbers are one-based and must be between 1 and 20.
            """)
    public String loadMusicResultsPage(@P("Requested music result page from 1 to 20") int page) {
        MusicRecommendationBo current = sessionStore.get(actionContext.memoryId()).orElse(null);
        if (current == null) {
            return "There is no recent music search. Call recommendMusic first.";
        }
        if (page < 1 || page > MusicRecommendationAo.MAX_PAGE) {
            return "Music result page must be between 1 and 20.";
        }
        int pageSize = Math.min(MusicRecommendationAo.MAX_PAGE_SIZE, Math.max(1, current.pageSize()));
        try {
            var memoryId = actionContext.memoryId();
            MusicRecommendationBo recommendation = recommendationService.recommend(
                    new MusicRecommendationAo(memoryId.userId(), memoryId.conversationId(),
                            current.description(), page, pageSize));
            sessionStore.put(actionContext.memoryId(), recommendation);
            actionContext.add(AgentActionBo.showMusic(recommendation));
            return "Loaded music result page " + page + ".\n" + summarize(recommendation);
        } catch (AppException exception) {
            return "Music catalog request failed: " + exception.getMessage();
        } catch (RuntimeException exception) {
            return "Music catalog request failed temporarily. Ask the user to retry later.";
        }
    }

    private static String summarize(MusicRecommendationBo recommendation) {
        List<MusicTrackBo> tracks = recommendation.tracks();
        if (tracks.isEmpty()) {
            return "The catalog search completed but found no reliable matches. " + recommendation.explanation();
        }
        String numberedTracks = IntStream.range(0, tracks.size())
                .mapToObj(index -> (index + 1) + ". " + tracks.get(index).name()
                        + " — " + artists(tracks.get(index)))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        String understanding = recommendation.understanding() != null && recommendation.understanding().resolved()
                ? "Resolved entity: " + recommendation.understanding().canonicalName() + ". " : "";
        return understanding + "Found " + recommendation.verifiedCount() + " metadata-validated results and "
                + recommendation.relatedCount() + " direct candidates or supplemental results; displayed "
                + tracks.size() + " playable tracks in the Sonora music panel. Search provenance is not proof "
                + "of an official relationship.\n"
                + numberedTracks;
    }

    private static String artists(MusicTrackBo track) {
        return track.artists() == null || track.artists().isEmpty()
                ? "Unknown artist"
                : String.join(" / ", track.artists());
    }

    private static String profileSummary(MusicProfileSummaryVo summary) {
        if (summary == null) return "当前还没有可用的音乐画像摘要。";
        StringBuilder result = new StringBuilder()
                .append("画像阶段：").append(summary.stageLabel())
                .append("（结论可信度：").append(summary.confidenceLabel()).append("）\n")
                .append(summary.headline()).append("。\n")
                .append(summary.overview());
        appendInsights(result, "\n\n喜欢与偏好", summary.likes());
        appendInsights(result, "\n\n避开与不喜欢", summary.avoids());
        if (!summary.observations().isEmpty()) {
            result.append("\n\n画像说明");
            for (String observation : summary.observations()) result.append("\n- ").append(observation);
        }
        return result.toString();
    }

    private static void appendInsights(StringBuilder result, String title,
                                       List<MusicProfileInsightVo> insights) {
        if (insights == null || insights.isEmpty()) return;
        result.append(title);
        for (MusicProfileInsightVo insight : insights) {
            result.append("\n- ").append(insight.typeLabel()).append("：").append(insight.value())
                    .append("（").append(insight.basis());
            if ("L2".equals(insight.layer())) {
                result.append("，置信度 ").append(Math.round(insight.confidence() * 100)).append("%");
            }
            result.append("）");
        }
    }
}
