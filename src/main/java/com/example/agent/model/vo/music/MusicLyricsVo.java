package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicLyricsBo;

import java.util.List;

public record MusicLyricsVo(
        String provider,
        String trackId,
        boolean available,
        boolean synced,
        List<MusicLyricLineVo> lines,
        String source,
        String message
) {
    public static MusicLyricsVo from(MusicLyricsBo lyrics) {
        return new MusicLyricsVo(lyrics.provider(), lyrics.trackId(), lyrics.available(), lyrics.synced(),
                lyrics.lines().stream().map(MusicLyricLineVo::from).toList(), lyrics.source(), lyrics.message());
    }
}
