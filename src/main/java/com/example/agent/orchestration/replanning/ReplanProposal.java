package com.example.agent.orchestration.replanning;

import com.example.agent.agent.contract.planning.PlanTask;

import java.util.List;

/** Strategy output. Replacement is validated by BoundedReplanner before it can enter the runtime. */
public record ReplanProposal(
        Kind kind,
        List<PlanTask> replacementTasks,
        String waitingSlot,
        String message
) {
    public ReplanProposal {
        kind = kind == null ? Kind.FAIL : kind;
        replacementTasks = replacementTasks == null ? List.of() : List.copyOf(replacementTasks);
        waitingSlot = waitingSlot == null ? "" : waitingSlot.strip();
        message = message == null ? "" : message.strip();
        if (kind == Kind.REPLACE && replacementTasks.isEmpty()) {
            throw new IllegalArgumentException("替换方案必须包含任务");
        }
        if (kind == Kind.ASK_USER && waitingSlot.isEmpty()) {
            throw new IllegalArgumentException("ASK_USER 方案必须声明等待槽位");
        }
    }

    public enum Kind { REPLACE, ASK_USER, FAIL }

    public static ReplanProposal replace(List<PlanTask> tasks, String message) {
        return new ReplanProposal(Kind.REPLACE, tasks, "", message);
    }

    public static ReplanProposal askUser(String slot, String message) {
        return new ReplanProposal(Kind.ASK_USER, List.of(), slot, message);
    }

    public static ReplanProposal fail(String message) {
        return new ReplanProposal(Kind.FAIL, List.of(), "", message);
    }
}
