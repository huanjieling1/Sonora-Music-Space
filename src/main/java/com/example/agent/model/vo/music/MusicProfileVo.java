package com.example.agent.model.vo.music;

import java.util.List;

public record MusicProfileVo(List<MusicPreferenceVo> explicitPreferences,
                             List<MusicPreferenceVo> inferredPreferences,
                             int labeledEvents,
                             int exposures,
                             MusicProfileSummaryVo summary) {
}
