package com.example.agent.agent.contract;

import java.util.List;

/** Literal evidence extracted from the user's current wording before any model proposal is trusted. */
public record MusicIntentEvidence(
        boolean explicitMusicAction,
        boolean explicitMusicTarget,
        boolean trend,
        boolean emotional,
        boolean safety,
        boolean followUp,
        List<String> terms
) {
    public MusicIntentEvidence {
        terms = terms == null ? List.of() : terms.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .limit(12)
                .toList();
    }

    public boolean explicitMusicRequest() {
        return explicitMusicAction || explicitMusicTarget || trend || followUp;
    }

    public boolean supportCandidate() {
        return emotional || safety;
    }
}
