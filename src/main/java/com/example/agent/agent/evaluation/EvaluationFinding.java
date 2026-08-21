package com.example.agent.agent.evaluation;

/** One machine-readable reason behind an evaluation control signal. */
public record EvaluationFinding(
        String code,
        EvaluationDecision decision,
        String subject,
        String message
) {
    public EvaluationFinding {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("验收发现必须有错误码");
        if (decision == null || decision == EvaluationDecision.PASS) {
            throw new IllegalArgumentException("验收发现必须携带非 PASS 结论");
        }
        code = code.strip();
        subject = subject == null ? "" : subject.strip();
        message = message == null ? "" : message.strip();
    }
}
