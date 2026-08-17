package com.example.agent.service;

import com.example.agent.model.dto.music.MusicBehaviorEventRequest;
import com.example.agent.model.dto.music.MusicPreferenceRequest;
import com.example.agent.model.vo.music.MusicEventVo;
import com.example.agent.model.vo.music.MusicPolicyStatusVo;
import com.example.agent.model.vo.music.MusicPreferenceVo;
import com.example.agent.model.vo.music.MusicProfileVo;

import java.util.UUID;

public interface MusicPersonalizationService {
    MusicEventVo recordEvent(long userId, MusicBehaviorEventRequest request);

    boolean isTrackLiked(long userId, UUID searchId, String trackId);

    boolean isTrackSaved(long userId, UUID searchId, String trackId);

    MusicProfileVo profile(long userId);

    MusicPreferenceVo addPreference(long userId, MusicPreferenceRequest request);

    void deletePreference(long userId, UUID preferenceId);

    int clearLearned(long userId);

    MusicPolicyStatusVo policyStatus(long userId);
}
