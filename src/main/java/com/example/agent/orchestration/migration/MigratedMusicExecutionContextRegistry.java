package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Identity-bound context for capability tasks; task arguments never carry user/session authority. */
@Component
public final class MigratedMusicExecutionContextRegistry {
    private final ConcurrentHashMap<UUID, Context> contexts = new ConcurrentHashMap<>();

    public void register(UUID workflowId, Context context) {
        if (workflowId == null || context == null) throw new IllegalArgumentException("迁移执行上下文不能为空");
        contexts.put(workflowId, context);
    }

    public Context require(UUID workflowId, String principalId) {
        Context context = Optional.ofNullable(contexts.get(workflowId))
                .orElseThrow(() -> new IllegalStateException("迁移工作流上下文不存在：" + workflowId));
        if (!String.valueOf(context.turn().userId()).equals(principalId)) {
            throw new SecurityException("迁移工作流用户身份不匹配");
        }
        return context;
    }

    public Optional<Context> find(UUID workflowId, String principalId) {
        if (workflowId == null || principalId == null || principalId.isBlank()) return Optional.empty();
        Context context = contexts.get(workflowId);
        if (context == null) return Optional.empty();
        if (!String.valueOf(context.turn().userId()).equals(principalId.strip())) {
            throw new SecurityException("迁移工作流用户身份不匹配");
        }
        return Optional.of(context);
    }

    public void release(UUID workflowId) {
        if (workflowId != null) contexts.remove(workflowId);
    }

    public record Context(MusicAgentTurn turn, UserTasteContext tasteContext, MusicTurnPlan followUpPlan,
                          UserGoalGraph goalGraph) {
        public Context {
            if (turn == null || goalGraph == null) throw new IllegalArgumentException("迁移执行必须绑定用户回合和目标图");
            followUpPlan = followUpPlan == null ? MusicTurnPlan.none() : followUpPlan;
        }
    }
}
