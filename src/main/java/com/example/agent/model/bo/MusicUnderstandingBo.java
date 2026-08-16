package com.example.agent.model.bo;

import java.util.List;

public record MusicUnderstandingBo(
        Long entityId,
        String canonicalName,
        MusicEntityType entityType,
        List<String> aliases,
        double confidence,
        List<String> knowledgeSources,
        List<String> relatedTerms,
        List<MusicTrackRelationBo> trackRelations,
        List<String> rejectedTrackIds
) {
    public static MusicUnderstandingBo unresolved() {
        return new MusicUnderstandingBo(null, null, MusicEntityType.UNKNOWN, List.of(), 0,
                List.of(), List.of(), List.of(), List.of());
    }

    public boolean resolved() {
        return entityId != null && canonicalName != null && !canonicalName.isBlank();
    }
}
