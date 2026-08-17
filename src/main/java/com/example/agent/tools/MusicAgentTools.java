package com.example.agent.tools;

import com.example.agent.exception.AppException;
import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.MusicRecommendationBo;
import com.example.agent.model.bo.MusicTrackBo;
import com.example.agent.model.bo.QqPlaylistSearchResultBo;
import com.example.agent.model.bo.QqArtistSearchResultBo;
import com.example.agent.model.bo.QqMusicSearchType;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicKeywordExtractor;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.QqMusicService;
import com.example.agent.service.impl.MusicAgentSessionStore;
import com.example.agent.service.impl.MusicRecommendationContextResolver;
import com.example.agent.service.impl.QqArtistProfileSummarizer;
import com.example.agent.model.vo.music.MusicProfileInsightVo;
import com.example.agent.model.vo.music.MusicProfileSummaryVo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

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

    @Autowired
    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext,
                           QqMusicService qqMusicService,
                           MusicKeywordExtractor keywordExtractor,
                           MusicRecommendationContextResolver recommendationContextResolver) {
        this.recommendationService = recommendationService;
        this.personalizationService = personalizationService;
        this.sessionStore = sessionStore;
        this.actionContext = actionContext;
        this.qqMusicService = qqMusicService;
        this.keywordExtractor = keywordExtractor;
        this.recommendationContextResolver = recommendationContextResolver;
    }

    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext,
                           QqMusicService qqMusicService,
                           MusicKeywordExtractor keywordExtractor) {
        this(recommendationService, personalizationService, sessionStore, actionContext,
                qqMusicService, keywordExtractor,
                new MusicRecommendationContextResolver(personalizationService));
    }

    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext,
                           QqMusicService qqMusicService) {
        this(recommendationService, personalizationService, sessionStore, actionContext,
                qqMusicService, null, new MusicRecommendationContextResolver(personalizationService));
    }

    public MusicAgentTools(MusicRecommendationService recommendationService,
                           MusicPersonalizationService personalizationService,
                           MusicAgentSessionStore sessionStore,
                           AgentActionContext actionContext) {
        this(recommendationService, personalizationService, sessionStore, actionContext, null, null,
                new MusicRecommendationContextResolver(personalizationService));
    }

    @Tool("""
            Analyze and summarize the current listener's stored music profile. Use this tool for requests such as
            "总结我的偏好", "分析我的音乐画像", "我喜欢什么", or "系统对我了解多少". Report only auditable
            explicit preferences, qualified behavioral inferences, evidence maturity, and limitations. This is not
            a catalog search: do not call another tool unless the user separately asks for songs or playlists.
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
        if (!StringUtils.hasText(description)) {
            return "Music search was not run because the request description is empty.";
        }
        try {
            var memoryId = actionContext.memoryId();
            MusicRecommendationBo recommendation = search(
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
        if (qqMusicService == null || keywordExtractor == null) {
            return recommendationService.recommend(command);
        }
        MusicKeywordExtractor.ExtractedKeyword extracted = keywordExtractor.extract(command.description());
        var context = recommendationContextResolver.resolve(
                command.userId(), command.description(), extracted.intent(), extracted.keyword());
        if (context.recommendation()) {
            MusicRecommendationBo recommendation = recommendationService.recommend(
                    new MusicRecommendationAo(command.userId(), command.conversationId(),
                            context.searchDescription(), command.page(), command.pageSize()));
            return withRecommendationRationale(recommendation, context.rationale());
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
