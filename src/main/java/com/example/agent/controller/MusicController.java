package com.example.agent.controller;

import com.example.agent.model.ao.MusicRecommendationAo;
import com.example.agent.model.dto.music.MusicRecommendationRequest;
import com.example.agent.model.dto.music.MusicFeedbackRequest;
import com.example.agent.model.dto.music.MusicBehaviorEventRequest;
import com.example.agent.model.dto.music.MusicPreferenceRequest;
import com.example.agent.model.dto.music.QqMusicSessionRequest;
import com.example.agent.model.dto.music.MusicCatalogSearchRequest;
import com.example.agent.model.vo.common.ApiResponse;
import com.example.agent.model.vo.music.MusicRecommendationVo;
import com.example.agent.model.vo.music.MusicFeedbackVo;
import com.example.agent.model.vo.music.MusicStatusVo;
import com.example.agent.model.vo.music.MusicEventVo;
import com.example.agent.model.vo.music.MusicPolicyStatusVo;
import com.example.agent.model.vo.music.MusicPreferenceVo;
import com.example.agent.model.vo.music.MusicProfileVo;
import com.example.agent.model.vo.music.QqMusicStatusVo;
import com.example.agent.model.vo.music.QqMusicQrLoginVo;
import com.example.agent.model.vo.music.MusicLyricsVo;
import com.example.agent.model.vo.music.MusicCatalogSearchVo;
import com.example.agent.model.vo.music.QqPublicPlaylistVo;
import com.example.agent.model.bo.MusicLyricsBo;
import com.example.agent.model.bo.QqMusicSearchBo;
import com.example.agent.model.bo.QqMusicSearchType;
import com.example.agent.model.bo.QqArtistDetailBo;
import com.example.agent.model.bo.QqChartCatalogBo;
import com.example.agent.model.bo.QqChartDetailBo;
import com.example.agent.model.bo.QqTrendReportBo;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicFeedbackService;
import com.example.agent.service.MusicPersonalizationService;
import com.example.agent.service.QqMusicService;
import com.example.agent.service.MusicCatalogSearchService;
import com.example.agent.service.QqMusicChartService;
import com.example.agent.security.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/music")
public class MusicController {
    private final MusicRecommendationService musicRecommendationService;
    private final MusicFeedbackService musicFeedbackService;
    private final QqMusicService qqMusicService;
    private final MusicPersonalizationService personalizationService;
    private final MusicCatalogSearchService catalogSearchService;
    private final QqMusicChartService chartService;

    public MusicController(MusicRecommendationService musicRecommendationService,
                           MusicFeedbackService musicFeedbackService,
                           QqMusicService qqMusicService,
                           MusicPersonalizationService personalizationService,
                           MusicCatalogSearchService catalogSearchService,
                           QqMusicChartService chartService) {
        this.musicRecommendationService = musicRecommendationService;
        this.musicFeedbackService = musicFeedbackService;
        this.qqMusicService = qqMusicService;
        this.personalizationService = personalizationService;
        this.catalogSearchService = catalogSearchService;
        this.chartService = chartService;
    }

    @PostMapping("/feedback")
    public ApiResponse<MusicFeedbackVo> feedback(@Valid @RequestBody MusicFeedbackRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("纠错已记录", musicFeedbackService.record(user.id(), request));
    }

    @GetMapping("/status")
    public ApiResponse<MusicStatusVo> status() {
        return ApiResponse.ok("获取成功", MusicStatusVo.from(musicRecommendationService.status()));
    }

    @PostMapping("/recommend")
    public ApiResponse<MusicRecommendationVo> recommend(
            @Valid @RequestBody MusicRecommendationRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        var command = new MusicRecommendationAo(user.id(), request.conversationId(), request.description().trim(),
                request.resolvedPage(), request.resolvedPageSize());
        return ApiResponse.ok("推荐完成", MusicRecommendationVo.from(
                musicRecommendationService.recommend(command)));
    }

    @PostMapping("/search")
    public ApiResponse<MusicCatalogSearchVo> search(
            @Valid @RequestBody MusicCatalogSearchRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("搜索完成", MusicCatalogSearchVo.from(catalogSearchService.search(
                user.id(), request.conversationId(), request.keyword().trim(), request.resolvedType(),
                request.resolvedPage(), request.resolvedPageSize())));
    }

    @PostMapping("/events")
    public ApiResponse<MusicEventVo> event(@Valid @RequestBody MusicBehaviorEventRequest request,
                                           @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("行为已记录", personalizationService.recordEvent(user.id(), request));
    }

    @GetMapping("/track-state")
    public ApiResponse<Map<String, Boolean>> trackState(
            @RequestParam UUID searchId,
            @RequestParam String trackId,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("获取成功", Map.of(
                "liked", personalizationService.isTrackLiked(user.id(), searchId, trackId),
                "saved", personalizationService.isTrackSaved(user.id(), searchId, trackId)));
    }

    @GetMapping("/profile")
    public ApiResponse<MusicProfileVo> profile(@AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("获取成功", personalizationService.profile(user.id()));
    }

    @PostMapping("/profile/preferences")
    public ApiResponse<MusicPreferenceVo> addPreference(
            @Valid @RequestBody MusicPreferenceRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("偏好已保存", personalizationService.addPreference(user.id(), request));
    }

    @DeleteMapping("/profile/preferences/{preferenceId}")
    public ApiResponse<Map<String, Boolean>> deletePreference(@PathVariable java.util.UUID preferenceId,
                                                               @AuthenticationPrincipal AppUserPrincipal user) {
        personalizationService.deletePreference(user.id(), preferenceId);
        return ApiResponse.ok("偏好已删除", Map.of("deleted", true));
    }

    @DeleteMapping("/profile/learned")
    public ApiResponse<Map<String, Integer>> clearLearned(@AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("学习画像已清除", Map.of("deleted", personalizationService.clearLearned(user.id())));
    }

    @GetMapping("/policy/status")
    public ApiResponse<MusicPolicyStatusVo> policyStatus(@AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("获取成功", personalizationService.policyStatus(user.id()));
    }

    @GetMapping("/qq/status")
    public ApiResponse<QqMusicStatusVo> qqStatus() {
        return ApiResponse.ok("获取成功", QqMusicStatusVo.from(qqMusicService.status()));
    }

    @GetMapping("/qq/home")
    public ApiResponse<List<QqPublicPlaylistVo>> qqHome(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int pageSize) {
        return ApiResponse.ok("QQ 音乐公开歌单加载完成", qqMusicService.publicPlaylists(page, pageSize)
                .stream().map(QqPublicPlaylistVo::from).toList());
    }

    @GetMapping("/qq/charts")
    public ApiResponse<QqChartCatalogBo> qqCharts() {
        return ApiResponse.ok("QQ 音乐官方榜单目录加载完成", chartService.catalog());
    }

    @GetMapping("/qq/charts/{chartId}")
    public ApiResponse<QqChartDetailBo> qqChart(
            @PathVariable int chartId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok("QQ 音乐官方榜单加载完成",
                chartService.chart(chartId, period, offset, limit));
    }

    @GetMapping("/qq/trending/artists")
    public ApiResponse<QqTrendReportBo> qqTrendingArtists(
            @RequestParam(defaultValue = "RECENT") String window,
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok("热门歌手趋势加载完成",
                chartService.trendingArtists(window, group, limit));
    }

    @GetMapping("/qq/artists/{artistMid}/top-tracks")
    public ApiResponse<QqTrendReportBo> qqArtistTopTracks(
            @PathVariable String artistMid,
            @RequestParam(required = false) String artistName,
            @RequestParam(defaultValue = "ALL_TIME") String window,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok("歌手榜单热门歌曲加载完成",
                chartService.artistTopTracks(artistMid, artistName, window, limit));
    }

    @GetMapping("/qq/search")
    public ApiResponse<QqMusicSearchBo> qqSearch(
            @RequestParam UUID conversationId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "TRACK") QqMusicSearchType type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("QQ 音乐搜索完成",
                qqMusicService.search(user.id(), conversationId, keyword, type, page, pageSize));
    }

    @GetMapping("/qq/playlists/{playlistId}")
    public ApiResponse<QqPublicPlaylistVo> qqPlaylist(
            @PathVariable String playlistId,
            @RequestParam UUID conversationId,
            @RequestParam(defaultValue = "60") int limit,
            @RequestParam(defaultValue = "false") boolean shuffle,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("QQ 音乐公开歌单加载完成", QqPublicPlaylistVo.from(
                qqMusicService.publicPlaylist(user.id(), conversationId, playlistId, limit, shuffle)));
    }

    @GetMapping("/qq/artists/{artistMid}")
    public ApiResponse<QqArtistDetailBo> qqArtist(
            @PathVariable String artistMid,
            @RequestParam UUID conversationId,
            @RequestParam(defaultValue = "1") int songPage,
            @RequestParam(defaultValue = "20") int songPageSize,
            @RequestParam(defaultValue = "1") int albumPage,
            @RequestParam(defaultValue = "12") int albumPageSize,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("QQ 音乐歌手资料加载完成", qqMusicService.artist(user.id(), conversationId,
                artistMid, songPage, songPageSize, albumPage, albumPageSize));
    }

    @GetMapping("/qq/albums/{albumMid}")
    public ApiResponse<com.example.agent.model.bo.QqAlbumDetailBo> qqAlbum(
            @PathVariable String albumMid,
            @RequestParam UUID conversationId,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("QQ 音乐专辑加载完成", qqMusicService.album(user.id(), conversationId, albumMid));
    }

    @GetMapping("/qq/videos/{videoId}")
    public ApiResponse<com.example.agent.model.bo.QqVideoPlaybackBo> qqVideo(@PathVariable String videoId) {
        return ApiResponse.ok("QQ 音乐视频播放地址加载完成", qqMusicService.video(videoId));
    }

    @PostMapping("/qq/session")
    public ApiResponse<QqMusicStatusVo> saveQqSession(@Valid @RequestBody QqMusicSessionRequest request) {
        return ApiResponse.ok("QQ 音乐登录态已安全保存在本机",
                QqMusicStatusVo.from(qqMusicService.saveSession(request.cookie())));
    }

    @DeleteMapping("/qq/session")
    public ApiResponse<QqMusicStatusVo> clearQqSession() {
        return ApiResponse.ok("QQ 音乐登录态已清除", QqMusicStatusVo.from(qqMusicService.clearSession()));
    }

    @PostMapping("/qq/login/qr")
    public ApiResponse<QqMusicQrLoginVo> startQqQrLogin(@AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("QQ 音乐登录二维码已生成",
                QqMusicQrLoginVo.from(qqMusicService.startQrLogin(user.id())));
    }

    @GetMapping("/qq/login/qr/{loginId}")
    public ApiResponse<QqMusicQrLoginVo> pollQqQrLogin(
            @PathVariable String loginId,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("QQ 音乐扫码状态已更新",
                QqMusicQrLoginVo.from(qqMusicService.pollQrLogin(user.id(), loginId)));
    }

    @DeleteMapping("/qq/login/qr/{loginId}")
    public ApiResponse<Map<String, Boolean>> cancelQqQrLogin(
            @PathVariable String loginId,
            @AuthenticationPrincipal AppUserPrincipal user) {
        qqMusicService.cancelQrLogin(user.id(), loginId);
        return ApiResponse.ok("QQ 音乐扫码登录已取消", Map.of("cancelled", true));
    }

    @GetMapping("/qq/play/{songMid}")
    public ApiResponse<Map<String, String>> playQqTrack(@PathVariable String songMid,
                                                        @RequestParam(required = false) String mediaId) {
        var playback = qqMusicService.resolvePlayback(songMid, mediaId);
        return ApiResponse.ok("播放地址已刷新", Map.of(
                "url", playback.url().toASCIIString(),
                "quality", playback.quality()));
    }

    @GetMapping("/lyrics")
    public ApiResponse<MusicLyricsVo> lyrics(@RequestParam String provider,
                                              @RequestParam String trackId) {
        String normalizedProvider = provider == null ? "" : provider.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedTrackId = trackId == null ? "" : trackId.trim();
        MusicLyricsBo lyrics;
        if ("qq".equals(normalizedProvider)) {
            String songMid = normalizedTrackId.startsWith("qq:")
                    ? normalizedTrackId.substring(3) : normalizedTrackId;
            lyrics = qqMusicService.lyrics(songMid);
        } else {
            lyrics = MusicLyricsBo.unavailable(normalizedProvider, normalizedTrackId,
                    "当前曲库暂未提供这首歌曲的歌词");
        }
        return ApiResponse.ok("歌词加载完成", MusicLyricsVo.from(lyrics));
    }
}
