package com.example.agent.agent.response;

import com.example.agent.agent.contract.MusicExecutionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * User-facing boundary for action workflows. It may normalize wording, but cannot
 * change tracks, actions or success state produced by the execution agent.
 */
@Component
public class MusicResponseAgent {
    public String respond(MusicExecutionResult result) {
        if (result == null || !StringUtils.hasText(result.factualAnswer())) {
            return "这次音乐操作没有产生可验证结果，请稍后重试。";
        }
        return result.factualAnswer();
    }
}
