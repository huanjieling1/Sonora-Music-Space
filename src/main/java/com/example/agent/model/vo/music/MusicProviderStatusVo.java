package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicProviderStatusBo;

import java.util.List;

public record MusicProviderStatusVo(
        String id,
        String name,
        boolean configured,
        List<String> playbackTypes
) {
    public static MusicProviderStatusVo from(MusicProviderStatusBo provider) {
        return new MusicProviderStatusVo(provider.id(), provider.name(), provider.configured(),
                provider.playbackTypes());
    }
}
