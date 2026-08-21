package com.example.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Operational rollout and alert thresholds for the generic planner. */
@ConfigurationProperties(prefix = "agent.planner")
public class PlannerOperationsProperties {
    private RolloutMode rolloutMode = RolloutMode.SHADOW;
    private boolean fallbackToLegacy = true;
    private boolean killSwitch;
    private int taskCountAlertThreshold = 20;
    private int eventHistoryLimit = 500;

    public RolloutMode getRolloutMode() { return rolloutMode; }
    public void setRolloutMode(RolloutMode rolloutMode) {
        this.rolloutMode = rolloutMode == null ? RolloutMode.SHADOW : rolloutMode;
    }
    public boolean isFallbackToLegacy() { return fallbackToLegacy; }
    public void setFallbackToLegacy(boolean fallbackToLegacy) { this.fallbackToLegacy = fallbackToLegacy; }
    public boolean isKillSwitch() { return killSwitch; }
    public void setKillSwitch(boolean killSwitch) { this.killSwitch = killSwitch; }
    public int getTaskCountAlertThreshold() { return taskCountAlertThreshold; }
    public void setTaskCountAlertThreshold(int value) {
        if (value < 1 || value > 24) throw new IllegalArgumentException("任务告警阈值必须在 1 到 24 之间");
        this.taskCountAlertThreshold = value;
    }
    public int getEventHistoryLimit() { return eventHistoryLimit; }
    public void setEventHistoryLimit(int value) {
        if (value < 10 || value > 10_000) throw new IllegalArgumentException("事件历史上限必须在 10 到 10000 之间");
        this.eventHistoryLimit = value;
    }

    public enum RolloutMode { SHADOW, READ_ONLY, FULL }
}
