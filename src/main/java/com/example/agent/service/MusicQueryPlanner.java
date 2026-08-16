package com.example.agent.service;

import com.example.agent.model.bo.MusicSearchPlan;

public interface MusicQueryPlanner {
    MusicSearchPlan plan(String description);
}
