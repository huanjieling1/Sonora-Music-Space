package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicLyricLineBo;

public record MusicLyricLineVo(Long timeMs, String text, String translation, String romanization) {
    public static MusicLyricLineVo from(MusicLyricLineBo line) {
        return new MusicLyricLineVo(line.timeMs(), line.text(), line.translation(), line.romanization());
    }
}
