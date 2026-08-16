package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicStatusBo;

import java.util.List;

public record MusicStatusVo(
        boolean ready,
        List<MusicProviderStatusVo> providers,
        String message
) {
    public static MusicStatusVo from(MusicStatusBo status) {
        return new MusicStatusVo(status.ready(),
                status.providers().stream().map(MusicProviderStatusVo::from).toList(), status.message());
    }
}
