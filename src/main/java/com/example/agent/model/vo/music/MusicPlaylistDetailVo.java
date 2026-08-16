package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicPersonalizationStatus;
import com.example.agent.model.bo.MusicPlaylistDetailBo;

import java.util.List;
import java.util.UUID;

public record MusicPlaylistDetailVo(
        MusicPlaylistVo playlist,
        UUID searchId,
        String policyVersion,
        MusicPersonalizationStatus personalizationStatus,
        List<MusicPlaylistTrackVo> tracks
) {
    public static MusicPlaylistDetailVo from(MusicPlaylistDetailBo detail) {
        return new MusicPlaylistDetailVo(MusicPlaylistVo.from(detail.playlist()), detail.searchId(),
                detail.policyVersion(), detail.personalizationStatus(),
                detail.tracks().stream().map(MusicPlaylistTrackVo::from).toList());
    }
}
