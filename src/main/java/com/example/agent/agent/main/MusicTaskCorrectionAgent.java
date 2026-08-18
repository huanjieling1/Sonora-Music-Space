package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicTaskEvaluation;
import org.springframework.stereotype.Component;

/** Produces a bounded, explicit correction brief for the next child-agent attempt. */
@Component
public final class MusicTaskCorrectionAgent {
    public MusicAgentTurn correct(MusicAgentTurn original, MusicTaskEvaluation evaluation, int attempt) {
        if (original == null || evaluation == null || evaluation.decision() != MusicTaskEvaluation.Decision.REVISE) {
            return original;
        }
        if (transientFailure(evaluation.reason())) return original;
        String instruction = "第 " + attempt + " 次执行未通过主 Agent 验收。问题："
                + evaluation.reason() + "。纠正要求：" + (evaluation.correction().isBlank()
                ? "重新执行并补齐与原始目标一致的真实结构化证据" : evaluation.correction());
        return new MusicAgentTurn(original.userId(), original.conversationId(), original.request(),
                true, instruction);
    }

    private static boolean transientFailure(String reason) {
        return reason != null && (reason.contains("网络") || reason.contains("超时")
                || reason.contains("暂时") || reason.contains("不可用"));
    }
}
