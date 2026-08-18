package com.example.agent.tools;

import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.exception.AppException;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.ao.PreparedMusicRecommendationAo;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.QqPlaylistSearchResultBo;
import com.example.agent.model.bo.QqArtistSearchResultBo;
import com.example.agent.model.bo.QqMusicSearchType;
import com.example.agent.model.bo.QqChartResultBo;
import com.example.agent.model.bo.QqChartCatalogBo;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicKeywordExtractor;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.QqMusicService;
import com.example.agent.service.QqMusicChartService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.service.impl.MusicRecommendationContextResolver;
import com.example.agent.service.impl.QqArtistProfileSummarizer;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.IntStream;

@Component
public class MusicAgentTools {
    private final MusicRecommendationService recommendationService;
    private final MusicPersonalizationService personalizationService;
    private final MusicAgentSessionStore sessionStore;
    private final AgentActionContext actionContext;
    private final QqMusicService qqMusicService;
    private final MusicKeywordExtractor keywordExtractor;
    private final MusicRecommendationContextResolver recommendationContextResolver;
    private final QqMusicChartService chartService;

    @Autowired
    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext,
                           QqMusicService qqMusicService,
                           MusicKeywordExtractor keywordExtractor,
                           MusicRecommendationContextResolver recommendationContextResolver,
                           QqMusicChartService chartService) {
        this.recommendationService = recommendationService;
        this.personalizationService = personalizationService;
        this.sessionStore = sessionStore;
        this.actionContext = actionContext;
        this.qqMusicService = qqMusicService;
        this.keywordExtractor = keywordExtractor;
        this.recommendationContextResolver = recommendationContextResolver;
        this.chartService = chartService;
    }

    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext,
                           QqMusicService qqMusicService,
                           MusicKeywordExtractor keywordExtractor) {
        this(recommendationService, personalizationService, sessionStore, actionContext,
                qqMusicService, keywordExtractor,
                new MusicRecommendationContextResolver(personalizationService), null);
    }

    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext,
                           QqMusicService qqMusicService) {
        this(recommendationService, personalizationService, sessionStore, actionContext,
                qqMusicService, null, new MusicRecommendationContextResolver(personalizationService), null);
    }

    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext) {
        this(recommendationService, personalizationService, sessionStore, actionContext, null, null,
                new MusicRecommendationContextResolver(personalizationService), null);
    }

    @Tool("""
            Analyze and summarize the current listener's stored music profile. Use this tool for requests such as
            "总结我的偏好", "分析我的音乐画像", "我喜欢什么", or "系统对我了解多少". Report only auditable
            explicit preferences, qualified behavioral inferences, evidence maturity, and limitations. This is not
            a catalog search: do not call another tool unless the user separately asks for songs or playlists.
            """)
    public String summarizeMusicProfile() {
        try {
            return profileSummary(personalizationService.profile(actionContext.memoryId().userId()));
        } catch (AppException exception) {
            return "读取音乐画像失败：" + exception.getMessage();
        } catch (RuntimeException exception) {
            return "音乐画像暂时无法读取，请稍后重试。";
        }
    }

    @Tool("""
            Search QQ Music for a track, artist, album, game, film, anime, event, franchise, genre, mood or scene.
            Use this tool for individual-track music search or recommendation requests. Explicit public-playlist
            searches must use searchQqPlaylists instead. For a named entity search, preserve the listener's wording
            verbatim without aliases or invented qualifiers. For an open-ended recommendation, the tool reads the
            stored music profile, combines reliable preferences with the current scene, recalls a wider candidate
            pool, and applies personalized ranking. It displays page 1 as structured chat cards; use
            loadMusicResultsPage for later pages.
            """)
    public String recommendMusic(
            @P("The listener's original music wording with named entities copied verbatim; no query expansion")
            String description) {
        return recommendMusic(description, null, false, false);
    }

    /** Coordinator-only entry: the supplied snapshot is the sole profile source for this turn. */
    public String recommendMusic(String description, UserTasteContext tasteContext) {
        return recommendMusic(description, tasteContext, true, false);
    }

    /** Coordinator-only entry with an explicit request to avoid tracks shown in recent batches. */
    public String recommendMusic(String description, UserTasteContext tasteContext, boolean refreshBatch) {
        return recommendMusic(description, tasteContext, true, refreshBatch);
    }

    private String recommendMusic(String description, UserTasteContext tasteContext,
                                  boolean explicitProfileContext, boolean refreshBatch) {
        if (!StringUtils.hasText(description)) {
            return "Music search was not run because the request description is empty.";
        }
        try {
            var memoryId = actionContext.memoryId();
            MusicRecommendationBo recommendation = search(
                    new MusicRecommendationAo(memoryId.userId(), memoryId.conversationId(), description.trim(),
                            1, MusicRecommendationAo.MAX_PAGE_SIZE, refreshBatch), tasteContext,
                    explicitProfileContext);
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
            Search QQ Music for real public playlists matching the listener's named entity or keyword. Use this tool
            when the listener explicitly asks to find, search, or recommend playlists, rather than individual tracks.
            Preserve an explicitly named entity verbatim. For open-ended playlist recommendations, use the listener's
            stored profile and current scene to derive recommendation directions; if the profile is still empty, use
            QQ Music popular public playlists for cold start. The tool returns trusted playlist cards with cover art,
            creator, track count, listen count, and an auditable recommendation explanation. Searching alone never
            starts playback.
            """)
    public String searchQqPlaylists(
            @P("The listener's original playlist search wording with named entities copied verbatim")
            String description) {
        if (!StringUtils.hasText(description)) {
            return "未执行 QQ 音乐歌单搜索：搜索描述为空。";
        }
        if (qqMusicService == null) {
            return "QQ 音乐歌单搜索工具当前不可用。";
        }
        try {
            var memoryId = actionContext.memoryId();
            var extracted = keywordExtractor == null ? null : keywordExtractor.extract(description);
            String keyword = extracted == null ? description.trim() : extracted.keyword();
            var context = recommendationContextResolver.resolve(
                    memoryId.userId(), description,
                    extracted == null ? com.example.agent.model.bo.MusicSearchIntent.AMBIGUOUS : extracted.intent(),
                    keyword);
            var playlistSearch = context.recommendation()
                    ? recommendedPlaylists(memoryId.userId(), memoryId.conversationId(), context)
                    : QqPlaylistSearchResultBo.from(qqMusicService.search(
                            memoryId.userId(), memoryId.conversationId(), keyword,
                            QqMusicSearchType.PLAYLIST, 1, 12));
            actionContext.add(AgentActionBo.showQqPlaylists(playlistSearch));
            if (playlistSearch.playlists().isEmpty()) {
                return "QQ 音乐没有找到与推荐方向“" + playlistSearch.keyword() + "”匹配的公开歌单。"
                        + (StringUtils.hasText(playlistSearch.explanation())
                        ? " " + playlistSearch.explanation() : "");
            }
            return "已按“" + playlistSearch.keyword() + "”在 QQ 音乐找到并展示 "
                    + playlistSearch.playlists().size() + " 个公开歌单卡片。"
                    + (StringUtils.hasText(playlistSearch.explanation())
                    ? " " + playlistSearch.explanation() : "");
        } catch (AppException exception) {
            return "QQ 音乐歌单搜索失败：" + exception.getMessage();
        } catch (RuntimeException exception) {
            return "QQ 音乐歌单搜索暂时不可用，请稍后重试。";
        }
    }

    @Tool("""
            Search QQ Music for real artists and return large artist dossier cards. Use this tool when the listener
            explicitly asks to find, search, introduce, or inspect a singer, artist, band, group, composer, or music
            creator. Preserve the artist wording verbatim. Each card contains the QQ Music portrait and biography,
            auditable achievement and style summaries, catalog totals, a first-page song and album preview, and a
            link to the complete paginated artist catalog. Do not use ordinary track or playlist cards for an artist
            lookup, and never invent awards or genres that are absent from the source profile.
            """)
    public String searchQqArtists(
            @P("The listener's original artist search wording with the artist name copied verbatim")
            String description) {
        if (!StringUtils.hasText(description)) {
            return "未执行 QQ 音乐艺人搜索：搜索描述为空。";
        }
        if (qqMusicService == null) {
            return "QQ 音乐艺人搜索工具当前不可用。";
        }
        try {
            var memoryId = actionContext.memoryId();
            var extracted = keywordExtractor == null ? null : keywordExtractor.extract(description);
            String keyword = artistKeyword(description, extracted == null ? null : extracted.keyword());
            var searchResult = qqMusicService.search(
                    memoryId.userId(), memoryId.conversationId(), keyword, QqMusicSearchType.ARTIST, 1, 5);
            List<QqArtistSearchResultBo.ArtistProfile> profiles = searchResult.artists().stream()
                    .limit(1)
                    .map(artist -> artistProfile(memoryId.userId(), memoryId.conversationId(), artist))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            var artistSearch = new QqArtistSearchResultBo(
                    searchResult.searchId(), searchResult.keyword(), searchResult.page(), searchResult.pageSize(),
                    searchResult.total(), searchResult.hasNext(), profiles);
            actionContext.add(AgentActionBo.showQqArtists(artistSearch));
            if (profiles.isEmpty()) {
                return "QQ 音乐没有找到与“" + keyword + "”匹配且可读取资料的艺人。";
            }
            String names = profiles.stream().map(QqArtistSearchResultBo.ArtistProfile::name)
                    .reduce((left, right) -> left + "、" + right).orElse(keyword);
            return "已在 QQ 音乐找到并展示最佳匹配艺人的大型档案卡：" + names
                    + "。卡片中的成就与曲风总结仅依据 QQ 音乐简介和目录统计；"
                    + "歌曲、专辑先展示首批内容，可从卡片进入艺人页翻阅完整目录。";
        } catch (AppException exception) {
            return "QQ 音乐艺人搜索失败：" + exception.getMessage();
        } catch (RuntimeException exception) {
            return "QQ 音乐艺人搜索暂时不可用，请稍后重试。";
        }
    }

    @Tool("""
            Read QQ Music official charts or Sonora trend aggregations derived from persisted official chart
            observations. Use for hot, rising, new, regional or genre chart requests, recently popular artists,
            and a named artist's chart-leading tracks over day/week/month/all-time windows. Never turn a trend
            request into keyword search. Preserve the source type, requested window, actual coverage dates and
            methodology; Sonora aggregate scores are not QQ Music official heat scores.
            """)
    public String queryQqMusicTrends(
            @P("The listener's complete original chart or trend request") String request) {
        if (!StringUtils.hasText(request)) return "未执行 QQ 音乐趋势查询：请求为空。";
        if (chartService == null) return "QQ 音乐榜单趋势工具当前不可用。";
        try {
            String window = trendWindow(request);
            String artistName = specificTrendArtist(request);
            if (StringUtils.hasText(artistName) && qqMusicService != null) {
                var memory = actionContext.memoryId();
                var found = qqMusicService.search(memory.userId(), memory.conversationId(), artistName,
                        QqMusicSearchType.ARTIST, 1, 5);
                if (!found.artists().isEmpty()) {
                    var artist = found.artists().get(0);
                    var report = chartService.artistTopTracks(artist.mid(), artist.name(), window, 20);
                    actionContext.add(AgentActionBo.showQqChart(QqChartResultBo.trend(report)));
                    return report.tracks().isEmpty()
                            ? "已核对 QQ 音乐榜单快照，但当前覆盖周期内没有找到“" + artist.name() + "”的上榜歌曲。"
                            : "已根据 QQ 音乐官方榜单观察生成“" + artist.name() + "”的热门歌曲排行；"
                            + coverageText(report.coverageStart(), report.coverageEnd())
                            + "，聚合分数不是 QQ 官方热度分。";
                }
            }
            if (request.matches("(?is).*(?:哪些|哪个|歌手|艺人|乐队).*(?:火|热|排行|榜).*$")) {
                var report = chartService.trendingArtists(window, trendGroup(request), 20);
                actionContext.add(AgentActionBo.showQqChart(QqChartResultBo.trend(report)));
                return "已根据 QQ 音乐官方榜单观察生成近期热门歌手排行；"
                        + coverageText(report.coverageStart(), report.coverageEnd())
                        + "，聚合分数不是 QQ 官方热度分。";
            }
            QqChartCatalogBo.Chart selected = selectChart(chartService.catalog(), request);
            if (selected == null) return "QQ 音乐当前没有返回与该条件匹配的官方榜单。";
            var detail = chartService.chart(selected.id(), selected.period(), 0, 100);
            actionContext.add(AgentActionBo.showQqChart(QqChartResultBo.official(detail)));
            return "已读取 QQ 音乐官方“" + selected.name() + "”，榜单周期为 "
                    + selected.period() + "，结果和来源信息已显示在卡片中。";
        } catch (AppException exception) {
            return "QQ 音乐榜单查询失败：" + exception.getMessage();
        } catch (RuntimeException exception) {
            return "QQ 音乐榜单查询暂时不可用，请稍后重试。";
        }
    }

    @Tool("""
            Randomly choose a real public playlist created by a QQ Music user, load its real tracks, display the
            playlist in Sonora, replace the visible queue with a shuffled order, and start the first verified
            playable track among a bounded set of candidates.
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
            MusicTrackBo playableTrack = firstPlayableQqTrack(playlist.tracks());
            List<MusicTrackBo> orderedTracks = prioritize(playlist.tracks(), playableTrack);
            MusicRecommendationBo recommendation = new MusicRecommendationBo(
                    playlist.searchId(), "随机播放 QQ 音乐公开歌单", playlist.name(),
                    "来自 QQ 音乐用户“" + playlist.creatorName() + "”创建的公开歌单“" + playlist.name() + "”。",
                    com.example.agent.model.bo.MusicUnderstandingBo.unresolved(), List.of("QQ 音乐"),
                    orderedTracks, orderedTracks.size(), 0, 1, orderedTracks.size(),
                    false, 1, playlist.policyVersion(), playlist.personalizationStatus());
            sessionStore.put(memoryId, recommendation);
            actionContext.add(AgentActionBo.showMusic(recommendation));
            actionContext.add(AgentActionBo.queueMusic(recommendation));
            if (playableTrack == null) {
                return "已随机选择 QQ 音乐公开歌单《" + playlist.name() + "》（创建者："
                        + playlist.creatorName() + "），并加载 " + orderedTracks.size()
                        + " 首歌曲到播放队列；当前账号在前 " + Math.min(5, orderedTracks.size())
                        + " 首中未找到可播放曲目，队列已保留，可手动选择其他歌曲。";
            }
            actionContext.add(AgentActionBo.playTrack(playableTrack));
            return "已随机选择 QQ 音乐公开歌单《" + playlist.name() + "》（创建者："
                    + playlist.creatorName() + "），并随机排列 " + orderedTracks.size() + " 首歌曲，从《"
                    + playableTrack.name() + "》开始播放。";
        } catch (AppException exception) {
            return "QQ 音乐公开歌单加载失败：" + exception.getMessage();
        } catch (RuntimeException exception) {
            return "QQ 音乐公开歌单暂时无法加载，请稍后重试。";
        }
    }

    private MusicTrackBo firstPlayableQqTrack(List<MusicTrackBo> tracks) {
        int limit = Math.min(5, tracks.size());
        for (int index = 0; index < limit; index++) {
            MusicTrackBo track = tracks.get(index);
            QqPlaybackIdentity identity = qqPlaybackIdentity(track);
            if (identity == null) continue;
            try {
                qqMusicService.resolvePlayback(identity.songMid(), identity.mediaId());
                return track;
            } catch (AppException exception) {
                if (exception.getStatus() != HttpStatus.NOT_FOUND) return null;
            } catch (RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    private static QqPlaybackIdentity qqPlaybackIdentity(MusicTrackBo track) {
        if (track == null || !StringUtils.hasText(track.id()) || !track.id().startsWith("qq:")) return null;
        String songMid = track.id().substring(3);
        if (!songMid.matches("[A-Za-z0-9]+")) return null;
        String mediaId = null;
        if (StringUtils.hasText(track.playbackUrl())) {
            try {
                mediaId = UriComponentsBuilder.fromUriString(track.playbackUrl()).build()
                        .getQueryParams().getFirst("mediaId");
            } catch (IllegalArgumentException ignored) {
                mediaId = null;
            }
        }
        return new QqPlaybackIdentity(songMid, mediaId);
    }

    private static List<MusicTrackBo> prioritize(List<MusicTrackBo> tracks, MusicTrackBo first) {
        if (first == null || tracks.isEmpty() || first.equals(tracks.get(0))) return List.copyOf(tracks);
        ArrayList<MusicTrackBo> ordered = new ArrayList<>(tracks.size());
        ordered.add(first);
        tracks.stream().filter(track -> !track.equals(first)).forEach(ordered::add);
        return List.copyOf(ordered);
    }

    private record QqPlaybackIdentity(String songMid, String mediaId) {
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
            MusicRecommendationBo recommendation = search(
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
        if ("qq-search-v1".equals(recommendation.policyVersion())) {
            return understanding + "Submitted the single keyword \"" + recommendation.searchQuery()
                    + "\" to QQ Music and displayed " + tracks.size()
                    + " playable results in QQ Music order as Sonora chat cards.\n" + numberedTracks;
        }
        return understanding + "Found " + recommendation.verifiedCount() + " metadata-validated results and "
                + recommendation.relatedCount() + " direct candidates or supplemental results; displayed "
                + tracks.size() + " playable tracks as Sonora chat cards. Search provenance is not proof "
                + "of an official relationship.\n"
                + numberedTracks;
    }

    private MusicRecommendationBo search(MusicRecommendationAo command) {
        return search(command, null, false);
    }

    private MusicRecommendationBo search(MusicRecommendationAo command, UserTasteContext tasteContext,
                                         boolean explicitProfileContext) {
        if (qqMusicService == null || keywordExtractor == null) {
            return recommendationService.recommend(command);
        }
        MusicKeywordExtractor.ExtractedKeyword extracted = keywordExtractor.extract(command.description());
        var context = explicitProfileContext
                ? recommendationContextResolver.resolve(command.userId(), command.description(),
                extracted.intent(), extracted.keyword(), tasteContext)
                : recommendationContextResolver.resolve(command.userId(), command.description(),
                extracted.intent(), extracted.keyword());
        if (context.recommendation()) {
            MusicRecommendationBo recommendation;
            if (explicitProfileContext) {
                recommendation = recommendationService.recommendPrepared(new PreparedMusicRecommendationAo(
                        command, extracted.proposedPlan(), context.searchDescription(),
                        context.preferredTerms(), context.avoidedTerms(), context.rationale(),
                        context.profileStage(), context.profileApplied()));
            } else {
                recommendation = recommendationService.recommend(
                        new MusicRecommendationAo(command.userId(), command.conversationId(),
                                context.searchDescription(), command.page(), command.pageSize()));
            }
            return explicitProfileContext ? recommendation
                    : withRecommendationRationale(recommendation, context.rationale());
        }
        var result = qqMusicService.search(command.userId(), command.conversationId(), extracted.keyword(),
                com.example.agent.model.bo.QqMusicSearchType.TRACK, command.page(), command.pageSize());
        String explanation = result.tracks().isEmpty()
                ? "QQ 音乐没有返回与关键词“" + extracted.keyword() + "”匹配的歌曲。"
                : "已将关键词“" + extracted.keyword() + "”原样提交给 QQ 音乐，并按 QQ 音乐返回顺序展示结果。";
        return new MusicRecommendationBo(result.searchId(), command.description(), extracted.keyword(),
                explanation, extracted.understanding(), List.of("qq"), result.tracks(),
                0, result.tracks().size(), result.page(), result.pageSize(), result.hasNext(),
                MusicRecommendationAo.MAX_PAGE, "qq-search-v1",
                com.example.agent.model.bo.MusicPersonalizationStatus.DISABLED);
    }

    private QqPlaylistSearchResultBo recommendedPlaylists(
            long userId, UUID conversationId,
            MusicRecommendationContextResolver.RecommendationContext context) {
        if (context.playlistKeywords().isEmpty()) {
            List<com.example.agent.model.bo.QqMusicSearchBo.Playlist> playlists = qqMusicService
                    .publicPlaylists(1, 12).stream()
                    .map(playlist -> new com.example.agent.model.bo.QqMusicSearchBo.Playlist(
                            playlist.id(), playlist.name(), playlist.description(), playlist.coverUrl(),
                            playlist.creatorName(), playlist.listenCount(), playlist.trackCount(),
                            playlist.externalUrl()))
                    .toList();
            return new QqPlaylistSearchResultBo(null, "QQ 音乐热门推荐", context.rationale(),
                    1, 12, playlists.size(), false, playlists);
        }

        LinkedHashMap<String, com.example.agent.model.bo.QqMusicSearchBo.Playlist> merged = new LinkedHashMap<>();
        long total = 0;
        boolean hasNext = false;
        UUID searchId = null;
        for (String keyword : context.playlistKeywords()) {
            var result = qqMusicService.search(
                    userId, conversationId, keyword, QqMusicSearchType.PLAYLIST, 1, 8);
            if (searchId == null) searchId = result.searchId();
            total += Math.max(0, result.total());
            hasNext |= result.hasNext();
            for (var playlist : result.playlists()) {
                merged.putIfAbsent(playlist.id(), playlist);
                if (merged.size() >= 12) break;
            }
            if (merged.size() >= 12) break;
        }
        String direction = String.join(" · ", context.playlistKeywords());
        return new QqPlaylistSearchResultBo(searchId, direction, context.rationale(),
                1, 12, total, hasNext, List.copyOf(merged.values()));
    }

    private QqArtistSearchResultBo.ArtistProfile artistProfile(
            long userId, UUID conversationId, com.example.agent.model.bo.QqMusicSearchBo.Artist artist) {
        try {
            var detail = qqMusicService.artist(userId, conversationId, artist.mid(), 1, 12, 1, 8);
            var summary = QqArtistProfileSummarizer.summarize(detail);
            return new QqArtistSearchResultBo.ArtistProfile(
                    detail.mid(), detail.name(), detail.imageUrl(), detail.foreignName(), detail.birthday(),
                    detail.area(), detail.description(), detail.externalUrl(), detail.songTotal(), detail.albumTotal(),
                    artist.videoCount(), detail.hasMoreSongs(), detail.hasMoreAlbums(), detail.tracks(), detail.albums(),
                    summary.biography(), summary.achievements(), summary.style());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String artistKeyword(String description, String extractedKeyword) {
        String candidate = StringUtils.hasText(extractedKeyword) ? extractedKeyword.strip() : description.strip();
        candidate = candidate
                .replaceFirst("(?i)^(?:请|帮我|给我|我想|想要)?\\s*(?:搜索|搜一下|搜|查找|查一下|找找|找|介绍一下|介绍|了解一下|了解|search|find|introduce)\\s*", "")
                .replaceFirst("(?i)^(?:一下)?\\s*(?:歌手|艺人|乐队|组合|音乐人|创作人|composer|singer|artist|band|group)[:：\\s]*", "")
                .replaceFirst("(?i)\\s*(?:，|,|并且|并)?\\s*(?:帮我)?(?:介绍|总结|分析|告诉我|看看|了解).*$", "")
                .replaceFirst("(?i)\\s*(?:的)?(?:资料|档案|生涯|成就|曲风|风格|作品)(?:有哪些|是什么|怎么样)?[？?。.]?$", "")
                .strip();
        return StringUtils.hasText(candidate) ? candidate : description.strip();
    }

    private static String trendWindow(String request) {
        if (request.matches("(?is).*(?:全时间|历史|有史以来|all.?time).*$")) return "ALL_TIME";
        if (request.matches("(?is).*(?:一个月|本月|近 ?30 ?天|month).*$")) return "MONTH";
        if (request.matches("(?is).*(?:一周|本周|近 ?7 ?天|week).*$")) return "WEEK";
        if (request.matches("(?is).*(?:今日|今天|最近几天|日榜|day).*$")) return "DAY";
        return "RECENT";
    }

    private static String trendGroup(String request) {
        for (String group : List.of("地区榜", "特色榜", "全球榜", "巅峰榜")) {
            if (request.contains(group)) return group;
        }
        return null;
    }

    private static String specificTrendArtist(String request) {
        String value = request.strip()
                .replaceFirst("(?is)^(?:请|请你|帮我|给我|我想|想看|查看|查找|搜索|推荐|看看|看下|来点)\\s*", "")
                .replaceAll("(?is)(?:最近几天|最近一周|近一周|本周|最近一个月|近一个月|本月|近期|最近|全时间|历史)", "")
                .replaceFirst("(?is)(?:旗下|的)?\\s*(?:最火|最热门|热度最高|热门|上榜|排行靠前).*?(?:歌曲|歌|作品).*$", "")
                .strip();
        if (!StringUtils.hasText(value) || value.length() > 40
                || value.matches("(?is).*(?:哪些|哪个|歌手|艺人|乐队|音乐|歌曲|排行榜|榜单).*$")) {
            return null;
        }
        return value;
    }

    private static QqChartCatalogBo.Chart selectChart(QqChartCatalogBo catalog, String request) {
        List<QqChartCatalogBo.Chart> charts = catalog == null ? List.of() : catalog.groups().stream()
                .flatMap(group -> group.charts().stream()).toList();
        String preferred = request.contains("飙升") ? "飙升榜"
                : request.matches("(?is).*(?:新歌|最新).*$") ? "新歌榜"
                : request.contains("流行指数") ? "流行指数榜"
                : request.contains("说唱") ? "说唱榜"
                : request.contains("电音") ? "电音榜"
                : request.contains("动漫") ? "动漫音乐榜"
                : request.contains("游戏") ? "游戏音乐榜"
                : request.contains("影视") ? "影视金曲榜"
                : request.contains("国风") ? "国风热歌榜"
                : request.contains("欧美") ? "欧美榜"
                : request.contains("韩国") || request.contains("韩语") ? "韩国榜"
                : request.contains("日本") || request.contains("日语") ? "日本榜"
                : request.contains("内地") ? "内地榜"
                : request.contains("香港") ? "香港地区榜"
                : request.contains("台湾") ? "台湾地区榜"
                : "热歌榜";
        return charts.stream().filter(chart -> chart.name().contains(preferred)
                        || preferred.contains(chart.name()))
                .findFirst().orElseGet(() -> charts.stream()
                        .filter(chart -> chart.name().contains("热歌榜")).findFirst().orElse(null));
    }

    private static String coverageText(java.time.LocalDate start, java.time.LocalDate end) {
        if (start == null || end == null) return "实际覆盖范围暂未确定";
        return "实际覆盖 " + start + " 至 " + end;
    }

    private static MusicRecommendationBo withRecommendationRationale(
            MusicRecommendationBo recommendation, String rationale) {
        if (!StringUtils.hasText(rationale)) return recommendation;
        String explanation = rationale + " " + recommendation.explanation();
        return new MusicRecommendationBo(
                recommendation.searchId(), recommendation.description(), recommendation.searchQuery(), explanation,
                recommendation.understanding(), recommendation.providers(), recommendation.tracks(),
                recommendation.verifiedCount(), recommendation.relatedCount(), recommendation.page(),
                recommendation.pageSize(), recommendation.hasNext(), recommendation.maxPages(),
                recommendation.policyVersion(), recommendation.personalizationStatus());
    }

    private static String artists(MusicTrackBo track) {
        return track.artists() == null || track.artists().isEmpty()
                ? "Unknown artist"
                : String.join(" / ", track.artists());
    }

    private static String profileSummary(MusicProfileVo profile) {
        MusicProfileSummaryVo summary = profile == null ? null : profile.summary();
        if (summary == null) return "当前还没有可用的音乐画像摘要。";
        StringBuilder result = new StringBuilder()
                .append("画像阶段：").append(summary.stageLabel())
                .append("（结论可信度：").append(summary.confidenceLabel()).append("）\n")
                .append(summary.headline()).append("。\n")
                .append(summary.overview());
        appendInsights(result, "\n\n喜欢与偏好", summary.likes());
        appendInsights(result, "\n\n避开与不喜欢", summary.avoids());
        if (profile.analytics() != null) {
            var analytics = profile.analytics();
            result.append("\n\n收听统计")
                    .append("\n- 有效播放：").append(analytics.playCount()).append(" 次")
                    .append("\n- 听过歌曲：").append(analytics.uniqueTracks()).append(" 首")
                    .append("\n- 完播率：").append(Math.round(analytics.completionRate() * 100)).append("%")
                    .append("\n- 累计时长：").append(Math.round(analytics.totalPlaybackMs() / 60000.0)).append(" 分钟");
            if (!analytics.topTracks().isEmpty()) {
                var track = analytics.topTracks().get(0);
                result.append("\n- 最常听歌曲：").append(track.title())
                        .append(track.artist() == null ? "" : " — " + track.artist())
                        .append("（").append(track.playCount()).append(" 次）");
            }
            if (!analytics.topArtists().isEmpty()) {
                var artist = analytics.topArtists().get(0);
                result.append("\n- 最常听歌手：").append(artist.name())
                        .append("（").append(artist.playCount()).append(" 次）");
            }
            if (!analytics.labels().isEmpty()) {
                result.append("\n\n用户标签");
                for (var label : analytics.labels()) {
                    result.append("\n- ").append(label.name()).append("：").append(label.basis());
                }
            } else if (!analytics.profileReady()) {
                result.append("\n\n用户标签尚未生成：至少需要 ")
                        .append(analytics.requiredPlayCount()).append(" 次有效播放和 ")
                        .append(analytics.requiredUniqueTracks()).append(" 首不同歌曲。");
            }
        }
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
