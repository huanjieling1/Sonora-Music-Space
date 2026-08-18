package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicTurnPlan;

@FunctionalInterface
public interface MusicFollowUpPlanner {
    MusicTurnPlan plan(String contextPacket);
}
