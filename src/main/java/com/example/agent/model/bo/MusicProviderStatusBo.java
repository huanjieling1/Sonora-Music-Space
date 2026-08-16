package com.example.agent.model.bo;

import java.util.List;

public record MusicProviderStatusBo(
        String id,
        String name,
        boolean configured,
        List<String> playbackTypes
) {
}
