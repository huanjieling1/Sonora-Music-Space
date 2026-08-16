package com.example.agent.model.vo.music;

import com.example.agent.model.bo.QqPublicPlaylistBo;

import java.util.List;
import java.util.UUID;

public record QqPublicPlaylistVo(
        String id,
        String name,
        String description,
        String coverUrl,
        String creatorName,
        String creatorAvatarUrl,
        long listenCount,
        int trackCount,
        List<String> tags,
        String externalUrl,
        UUID searchId,
        List<MusicTrackVo> tracks,
        String policyVersion,
        String personalizationStatus
) {
    public static QqPublicPlaylistVo from(QqPublicPlaylistBo playlist) {
        return new QqPublicPlaylistVo(playlist.id(), playlist.name(), playlist.description(),
                playlist.coverUrl(), playlist.creatorName(), playlist.creatorAvatarUrl(),
                playlist.listenCount(), playlist.trackCount(), playlist.tags(), playlist.externalUrl(),
                playlist.searchId(), playlist.tracks().stream().map(MusicTrackVo::from).toList(),
                playlist.policyVersion(), playlist.personalizationStatus().name());
    }
}

