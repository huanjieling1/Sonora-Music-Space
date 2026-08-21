package com.example.agent.orchestration.migration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.agent.contract.MusicWorkflowSnapshot;
import com.example.agent.agent.contract.MusicWorkflowStatus;
import com.example.agent.agent.contract.MusicWorkflowTaskSnapshot;
import com.example.agent.agent.contract.MusicWorkflowTaskStatus;
import com.example.agent.agent.main.MusicGoalUnderstanding;
import com.example.agent.orchestration.dag.DagTaskStatus;
import com.example.agent.orchestration.dag.DagWorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Adapts the generic DAG response into the stable chat contract consumed by the current UI. */
@Component
public final class MigratedMusicWorkflowResponseAdapter {
    public MusicAgentWorkflowState adapt(MusicAgentTurn currentTurn, MusicGoalUnderstanding understanding,
                                         MigratedMusicWorkflowResult result) {
        var snapshot = result.snapshot();
        Map<String, com.example.agent.agent.contract.planning.PlanTask> tasks = snapshot.plan().tasks().stream()
                .collect(Collectors.toMap(com.example.agent.agent.contract.planning.PlanTask::id,
                        Function.identity(), (left, right) -> left, java.util.LinkedHashMap::new));
        List<MusicWorkflowTaskSnapshot> taskSnapshots = snapshot.tasks().stream().map(state -> {
            var task = tasks.get(state.taskId());
            return new MusicWorkflowTaskSnapshot(state.taskId(), task == null ? state.taskId() : task.title(),
                    task == null ? "generic-dag" : task.capabilityId(), taskStatus(state.status()),
                    state.attempts(), task == null ? 1 : task.maxAttempts(), state.message());
        }).toList();
        MusicWorkflowSnapshot workflow = new MusicWorkflowSnapshot(snapshot.workflowId(),
                result.goalGraph().originalRequest(), workflowStatus(snapshot.status()), taskSnapshots);
        MusicAgentRoute route = understanding == null ? inferRoute(snapshot.plan().tasks().stream()
                .map(com.example.agent.agent.contract.planning.PlanTask::capabilityId).toList())
                : understanding.route();
        return new MusicAgentWorkflowState(currentTurn, route,
                understanding == null ? null : understanding.understanding(), null, null,
                result.response().answer(), List.of("intent", "planner", "generic-dag", "response-guard"),
                workflow, MusicSupportContext.none(), null);
    }

    private static MusicWorkflowStatus workflowStatus(DagWorkflowStatus value) {
        return switch (value) {
            case RUNNING -> MusicWorkflowStatus.RUNNING;
            case REPLANNING -> MusicWorkflowStatus.REPLANNING;
            case WAITING_USER -> MusicWorkflowStatus.WAITING_USER;
            case COMPLETED -> MusicWorkflowStatus.COMPLETED;
            case FAILED, CANCELLED -> MusicWorkflowStatus.FAILED;
        };
    }

    private static MusicWorkflowTaskStatus taskStatus(DagTaskStatus value) {
        return switch (value) {
            case PENDING, READY -> MusicWorkflowTaskStatus.PENDING;
            case RUNNING -> MusicWorkflowTaskStatus.RUNNING;
            case RETRYING -> MusicWorkflowTaskStatus.RETRYING;
            case WAITING_USER -> MusicWorkflowTaskStatus.WAITING_USER;
            case COMPLETED -> MusicWorkflowTaskStatus.COMPLETED;
            case FAILED -> MusicWorkflowTaskStatus.FAILED;
            case SKIPPED, CANCELLED -> MusicWorkflowTaskStatus.SKIPPED;
        };
    }

    private static MusicAgentRoute inferRoute(List<String> capabilities) {
        if (capabilities.contains("music.playback.play")) return MusicAgentRoute.RESULT_PLAYBACK;
        if (capabilities.contains("music.queue.add")) return MusicAgentRoute.QUEUE_CONTROL;
        if (capabilities.contains("music.track.search")) return MusicAgentRoute.MUSIC_DISCOVERY;
        if (capabilities.contains("qq.artist.lookup")) return MusicAgentRoute.ARTIST_LOOKUP;
        if (capabilities.contains("qq.playlist.search")) return MusicAgentRoute.PLAYLIST_SEARCH;
        if (capabilities.contains("qq.chart.read")) return MusicAgentRoute.QQ_TREND_DISCOVERY;
        if (capabilities.contains("profile.music.read")) return MusicAgentRoute.PROFILE_ANALYSIS;
        return MusicAgentRoute.CONVERSATION;
    }
}
