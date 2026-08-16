package com.example.agent.model.bo;

import java.util.List;

public record MusicStatusBo(
        boolean ready,
        List<MusicProviderStatusBo> providers,
        String message
) {
}
