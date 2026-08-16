package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicPlaylistTrackBo;

public record MusicPlaylistTrackVo(long playlistTrackId, int position, MusicTrackVo track) {
    public static MusicPlaylistTrackVo from(MusicPlaylistTrackBo item) {
        return new MusicPlaylistTrackVo(item.playlistTrackId(), item.position(), MusicTrackVo.from(item.track()));
    }
}
