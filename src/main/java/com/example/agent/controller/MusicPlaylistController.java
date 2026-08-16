package com.example.agent.controller;

import com.example.agent.model.dto.music.MusicPlaylistCreateRequest;
import com.example.agent.model.dto.music.MusicPlaylistFromExposureRequest;
import com.example.agent.model.dto.music.MusicPlaylistOpenRequest;
import com.example.agent.model.dto.music.MusicPlaylistRecommendationRequest;
import com.example.agent.model.dto.music.MusicPlaylistTrackRequest;
import com.example.agent.model.dto.music.MusicPlaylistUpdateRequest;
import com.example.agent.model.vo.common.ApiResponse;
import com.example.agent.model.vo.music.MusicPlaylistDetailVo;
import com.example.agent.model.vo.music.MusicPlaylistVo;
import com.example.agent.security.AppUserPrincipal;
import com.example.agent.service.MusicPlaylistService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/music/playlists")
public class MusicPlaylistController {
    private final MusicPlaylistService playlists;

    public MusicPlaylistController(MusicPlaylistService playlists) {
        this.playlists = playlists;
    }

    @GetMapping
    public ApiResponse<List<MusicPlaylistVo>> list(@AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("获取成功", playlists.list(user.id()).stream()
                .map(MusicPlaylistVo::from).toList());
    }

    @PostMapping
    public ApiResponse<MusicPlaylistVo> create(@Valid @RequestBody MusicPlaylistCreateRequest request,
                                                @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("歌单已创建", MusicPlaylistVo.from(
                playlists.create(user.id(), request.name(), request.description())));
    }

    @PostMapping("/from-exposure")
    public ApiResponse<MusicPlaylistVo> fromExposure(
            @Valid @RequestBody MusicPlaylistFromExposureRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("推荐已保存为歌单", MusicPlaylistVo.from(
                playlists.createFromExposure(user.id(), request.searchId(), request.name(),
                        request.description())));
    }

    @PostMapping("/recommended")
    public ApiResponse<MusicPlaylistVo> recommended(
            @Valid @RequestBody MusicPlaylistRecommendationRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("专属歌单已生成", MusicPlaylistVo.from(
                playlists.createRecommended(user.id(), request.conversationId(), request.name(),
                        request.description())));
    }

    @PostMapping("/{playlistId}/open")
    public ApiResponse<MusicPlaylistDetailVo> open(
            @PathVariable UUID playlistId,
            @Valid @RequestBody MusicPlaylistOpenRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("歌单已打开", MusicPlaylistDetailVo.from(
                playlists.open(user.id(), playlistId, request.conversationId())));
    }

    @PatchMapping("/{playlistId}")
    public ApiResponse<MusicPlaylistVo> update(
            @PathVariable UUID playlistId,
            @Valid @RequestBody MusicPlaylistUpdateRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("歌单已更新", MusicPlaylistVo.from(
                playlists.update(user.id(), playlistId, request.name(), request.description())));
    }

    @DeleteMapping("/{playlistId}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable UUID playlistId,
                                                     @AuthenticationPrincipal AppUserPrincipal user) {
        playlists.delete(user.id(), playlistId);
        return ApiResponse.ok("歌单已删除", Map.of("deleted", true));
    }

    @PostMapping("/{playlistId}/tracks")
    public ApiResponse<MusicPlaylistVo> addTrack(
            @PathVariable UUID playlistId,
            @Valid @RequestBody MusicPlaylistTrackRequest request,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("歌曲已加入歌单", MusicPlaylistVo.from(
                playlists.addTrack(user.id(), playlistId, request.searchId(), request.trackId())));
    }

    @DeleteMapping("/{playlistId}/tracks/{playlistTrackId}")
    public ApiResponse<MusicPlaylistVo> removeTrack(
            @PathVariable UUID playlistId,
            @PathVariable long playlistTrackId,
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("歌曲已移出歌单", MusicPlaylistVo.from(
                playlists.removeTrack(user.id(), playlistId, playlistTrackId)));
    }
}
