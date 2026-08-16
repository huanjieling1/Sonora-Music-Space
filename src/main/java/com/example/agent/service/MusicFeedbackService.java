package com.example.agent.service;

import com.example.agent.model.dto.music.MusicFeedbackRequest;
import com.example.agent.model.vo.music.MusicFeedbackVo;

public interface MusicFeedbackService {
    MusicFeedbackVo record(long userId, MusicFeedbackRequest request);
}
