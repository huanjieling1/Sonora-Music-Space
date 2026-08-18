package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicChildAgentDescriptor;
import com.example.agent.agent.contract.MusicTaskEvaluation;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.contract.MusicTaskResult;
import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicWorkflowStatus;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import com.example.agent.agent.contract.MusicWorkflowTaskStatus;
import com.example.agent.agent.main.MusicWorkflowChildAgent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MusicWorkflowTaskSchedulerTest {
    @Test
    void resolvesByCapabilityAndReissuesAConcreteCorrectionWithinAttemptLimit() {
        List<MusicTaskInvocation> invocations = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        MusicWorkflowChildAgent child = agent("playlist-agent", "Playlist Agent", "qq.playlist.search", 100,
                invocation -> {
                    invocations.add(invocation);
                    int call = calls.incrementAndGet();
                    return new MusicTaskResult(invocation.task().id(), true, "batch-" + call,
                            List.of(), "result", "");
                });
        MusicWorkflowTaskScheduler scheduler = scheduler(child);
        MusicWorkflowRun run = run("qq.playlist.search", 2);
        MusicTaskInvocation initial = invocation(run, "五月天的歌单");

        var scheduled = scheduler.executeVerified(run, initial,
                result -> calls.get() == 1
                        ? MusicTaskEvaluation.revise("返回了歌曲", "只返回歌单")
                        : MusicTaskEvaluation.pass(),
                (evaluation, attempt) -> initial.withTurn(new MusicAgentTurn(1,
                        initial.turn().conversationId(), initial.turn().request(), true,
                        evaluation.correction())));

        assertThat(scheduled.evaluation().decision()).isEqualTo(MusicTaskEvaluation.Decision.PASS);
        assertThat(invocations).hasSize(2);
        assertThat(invocations.get(1).turn().request()).isEqualTo("五月天的歌单");
        assertThat(invocations.get(1).turn().executionDirective()).isEqualTo("只返回歌单");
        assertThat(run.snapshot().tasks()).singleElement().satisfies(task -> {
            assertThat(task.assignedAgent()).isEqualTo("Playlist Agent");
            assertThat(task.attempts()).isEqualTo(2);
            assertThat(task.status()).isEqualTo(MusicWorkflowTaskStatus.COMPLETED);
        });
    }

    @Test
    void askUserPausesWorkflowInsteadOfPretendingTheTaskSucceeded() {
        MusicWorkflowChildAgent child = agent("playlist-agent", "Playlist Agent", "qq.playlist.search", 100,
                invocation -> new MusicTaskResult(invocation.task().id(), false, null,
                        List.of(), "missing artist", "MISSING_ENTITY"));
        MusicWorkflowRun run = run("qq.playlist.search", 1);

        var scheduled = scheduler(child).executeVerified(run, invocation(run, "他的歌单"),
                result -> new MusicTaskEvaluation(false, false, "缺少歌手名称",
                        MusicTaskEvaluation.Decision.ASK_USER, "你想查哪位歌手的歌单？"), null);

        assertThat(scheduled.evaluation().decision()).isEqualTo(MusicTaskEvaluation.Decision.ASK_USER);
        run.finish(false);
        assertThat(run.snapshot().status()).isEqualTo(MusicWorkflowStatus.WAITING_USER);
        assertThat(run.snapshot().tasks()).singleElement().satisfies(task -> {
            assertThat(task.status()).isEqualTo(MusicWorkflowTaskStatus.WAITING_USER);
            assertThat(task.message()).contains("哪位歌手");
        });
    }

    @Test
    void highestPriorityAgentWinsWithoutChangingSchedulerCode() {
        MusicWorkflowChildAgent fallback = agent("fallback", "Fallback", "music.search", 1,
                invocation -> result(invocation, "fallback"));
        MusicWorkflowChildAgent specialist = agent("specialist", "Specialist", "music.search", 50,
                invocation -> result(invocation, "specialist"));
        MusicWorkflowRun run = run("music.search", 1);

        var scheduled = scheduler(fallback, specialist).executeVerified(run, invocation(run, "搜索"),
                result -> MusicTaskEvaluation.pass(), null);

        assertThat(scheduled.childAgentId()).isEqualTo("specialist");
        assertThat(scheduled.result().payload()).isEqualTo("specialist");
    }

    private static MusicWorkflowTaskScheduler scheduler(MusicWorkflowChildAgent... agents) {
        return new MusicWorkflowTaskScheduler(new MusicWorkflowChildAgentRegistry(List.of(agents)));
    }

    private static MusicWorkflowRun run(String capability, int attempts) {
        var task = new MusicWorkflowTaskSpec("execution", "执行", capability, "Auto",
                List.of(), attempts);
        return new MusicWorkflowRun(new MusicWorkflowPlan(UUID.randomUUID(), "测试",
                MusicAgentRoute.MUSIC_DISCOVERY, List.of(task), 1));
    }

    private static MusicTaskInvocation invocation(MusicWorkflowRun run, String request) {
        return new MusicTaskInvocation(run.spec("execution"),
                new MusicAgentTurn(1, UUID.randomUUID(), request), MusicAgentRoute.MUSIC_DISCOVERY,
                null, java.util.Map.of());
    }

    private static MusicTaskResult result(MusicTaskInvocation invocation, String payload) {
        return new MusicTaskResult(invocation.task().id(), true, payload, List.of(), payload, "");
    }

    private static MusicWorkflowChildAgent agent(String id, String name, String capability, int priority,
                                                  java.util.function.Function<MusicTaskInvocation,
                                                          MusicTaskResult> execution) {
        return new MusicWorkflowChildAgent() {
            @Override public MusicChildAgentDescriptor descriptor() {
                return new MusicChildAgentDescriptor(id, name, Set.of(capability), priority);
            }

            @Override public MusicTaskResult execute(MusicTaskInvocation invocation) {
                return execution.apply(invocation);
            }
        };
    }
}
