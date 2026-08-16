package com.example.agent.model.vo.music;

import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicUnderstandingBo;

import java.util.List;

public record MusicUnderstandingVo(
        String canonicalName,
        MusicEntityType entityType,
        List<String> aliases,
        double confidence,
        List<String> knowledgeSources
) {
    public static MusicUnderstandingVo from(MusicUnderstandingBo understanding) {
        if (understanding == null || !understanding.resolved()) {
            return null;
        }
        return new MusicUnderstandingVo(understanding.canonicalName(), understanding.entityType(),
                understanding.aliases(), understanding.confidence(), understanding.knowledgeSources());
    }
}
