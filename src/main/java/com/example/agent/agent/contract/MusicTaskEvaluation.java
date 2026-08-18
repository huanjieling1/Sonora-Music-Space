package com.example.agent.agent.contract;

public record MusicTaskEvaluation(boolean passed, boolean retryable, String reason,
                                  Decision decision, String correction) {
    public MusicTaskEvaluation(boolean passed, boolean retryable, String reason) {
        this(passed, retryable, reason, passed ? Decision.PASS
                : retryable ? Decision.REVISE : Decision.FAIL, reason);
    }

    public MusicTaskEvaluation {
        reason = reason == null ? "" : reason.strip();
        decision = decision == null ? (passed ? Decision.PASS : Decision.FAIL) : decision;
        correction = correction == null ? "" : correction.strip();
    }

    public static MusicTaskEvaluation pass() {
        return new MusicTaskEvaluation(true, false, "结果已经通过验收", Decision.PASS, "");
    }

    public static MusicTaskEvaluation revise(String reason, String correction) {
        return new MusicTaskEvaluation(false, true, reason, Decision.REVISE, correction);
    }

    public static MusicTaskEvaluation fail(String reason) {
        return new MusicTaskEvaluation(false, false, reason, Decision.FAIL, "");
    }

    public enum Decision {
        PASS, REVISE, REPLAN, ASK_USER, FAIL
    }
}
