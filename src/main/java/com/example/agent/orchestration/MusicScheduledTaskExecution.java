package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicTaskEvaluation;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.contract.MusicTaskResult;

/** Final scheduler view of one task after bounded validation/correction attempts. */
public record MusicScheduledTaskExecution(
        MusicTaskResult result,
        MusicTaskEvaluation evaluation,
        MusicTaskInvocation invocation,
        String childAgentId
) {
}
