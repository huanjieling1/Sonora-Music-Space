package com.example.agent.agent.contract;

public record ProfileAgentResult(UserTasteContext context, String answer, boolean languageModelApplied) {
    public ProfileAgentResult {
        if (context == null) throw new IllegalArgumentException("画像上下文不能为空");
        answer = answer == null ? "" : answer.strip();
    }
}
