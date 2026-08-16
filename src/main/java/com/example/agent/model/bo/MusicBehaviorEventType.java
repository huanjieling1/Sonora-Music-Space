package com.example.agent.model.bo;

public enum MusicBehaviorEventType {
    PLAY_START(null),
    COMPLETE(1.0),
    SKIP(-1.0),
    LIKE(2.0),
    UNLIKE(null),
    DISLIKE(-3.0),
    SAVE(2.0),
    UNSAVE(null),
    REPEAT(3.0);

    private final Double reward;

    MusicBehaviorEventType(Double reward) {
        this.reward = reward;
    }

    public Double reward() {
        return reward;
    }
}
