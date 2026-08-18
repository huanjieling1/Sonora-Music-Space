package com.example.agent.model.vo.music;

import java.util.List;

public record MusicProfileVo(List<MusicPreferenceVo> explicitPreferences,
                             List<MusicPreferenceVo> inferredPreferences,
                             int labeledEvents,
                             int exposures,
                             MusicProfileSummaryVo summary,
                             MusicProfileAnalyticsVo analytics) {
    public MusicProfileVo(List<MusicPreferenceVo> explicitPreferences,
                          List<MusicPreferenceVo> inferredPreferences,
                          int labeledEvents, int exposures, MusicProfileSummaryVo summary) {
        this(explicitPreferences, inferredPreferences, labeledEvents, exposures, summary, null);
    }
}
