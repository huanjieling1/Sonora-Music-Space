package com.example.agent.orchestration.dag;

import com.example.agent.agent.contract.planning.CompiledPlan;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.goal.DeterministicMusicGoalParser;
import com.example.agent.agent.planner.GenericPlanSynthesizer;
import com.example.agent.agent.planner.PlanCompiler;
import com.example.agent.agent.planner.PlanValidationContext;
import com.example.agent.repository.GenericWorkflowExecutionRepository;
import com.example.agent.orchestration.replanning.ReplanRecord;
import com.example.agent.orchestration.replanning.ReplanResult;
import com.example.agent.orchestration.confirmation.ConfirmationRequest;
import com.example.agent.agent.capability.CapabilityConfirmationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JpaDagExecutionPersistenceIntegrationTest {
    @Autowired JpaDagExecutionPersistence persistence;
    @Autowired GenericWorkflowExecutionRepository repository;
    @Autowired GenericPlanSynthesizer synthesizer;
    @Autowired PlanCompiler compiler;
    @Autowired DeterministicMusicGoalParser parser;

    @Test
    void persistsAndRestoresCompiledPlanAndTaskProgressWithOwnerIsolation() {
        UserGoalGraph graph = parser.parse("搜索 Mili 的歌");
        CompiledPlan plan = compiler.compile(graph, synthesizer.synthesize(graph),
                PlanValidationContext.standard("integration-user"));
        UUID workflowId = UUID.randomUUID();
        Instant now = Instant.now();
        ConfirmationRequest confirmation = new ConfirmationRequest(UUID.randomUUID(), workflowId,
                "integration-user", plan.tasks().get(0).id(), plan.tasks().get(0).capabilityId(),
                CapabilityConfirmationPolicy.ALWAYS, Map.of("query", "Mili"), "idem-persisted",
                "确认执行吗？", ConfirmationRequest.Status.PENDING, now, now.plusSeconds(600), null);
        List<DagTaskState> tasks = plan.tasks().stream().map(task -> task.id().equals(plan.tasks().get(0).id())
                ? new DagTaskState(task.id(), DagTaskStatus.WAITING_USER, 0, "确认执行吗？",
                confirmation.replySlot(), "idem-persisted", null, null,
                "CONFIRMATION_REQUIRED", false, confirmation)
                : new DagTaskState(task.id(), DagTaskStatus.PENDING, 0, "", "", "", null)).toList();
        ReplanRecord record = new ReplanRecord(1, tasks.get(0).taskId(), "PROVIDER_UNAVAILABLE",
                Set.of(tasks.get(0).taskId()), Set.of(), "fingerprint-1",
                ReplanResult.Kind.APPLIED, "使用备用能力", now);
        DagExecutionSnapshot snapshot = new DagExecutionSnapshot(workflowId, "integration-user", "",
                plan, DagWorkflowStatus.RUNNING, tasks, Map.of(), now, now, List.of(record));
        try {
            persistence.save(snapshot);

            assertThat(persistence.load(workflowId, "integration-user")).contains(snapshot);
            assertThat(persistence.load(workflowId, "other-user")).isEmpty();
            assertThat(repository.findById(workflowId.toString())).isPresent().get().satisfies(entity -> {
                assertThat(entity.getPlanJson()).contains("executionStages", "music.track.search");
                assertThat(entity.getStateJson()).contains("PENDING", workflowId.toString(),
                        "PROVIDER_UNAVAILABLE", "fingerprint-1", "CONFIRMATION_REQUIRED",
                        confirmation.requestId().toString(), "idem-persisted");
            });
        } finally {
            repository.deleteById(workflowId.toString());
        }
    }

    @Test
    void findsWaitingWorkflowByConversationAndPersistsNonProfileResumeContext() {
        UserGoalGraph graph = parser.parse("搜索 Mili 的歌");
        CompiledPlan plan = compiler.compile(graph, synthesizer.synthesize(graph),
                PlanValidationContext.standard("resume-user"));
        UUID workflowId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Instant now = Instant.now();
        List<DagTaskState> tasks = plan.tasks().stream().map(task -> new DagTaskState(task.id(),
                task.id().equals(plan.tasks().get(0).id()) ? DagTaskStatus.WAITING_USER : DagTaskStatus.PENDING,
                0, "请补充关键词", task.id().equals(plan.tasks().get(0).id()) ? "query" : "",
                "", null)).toList();
        DagExecutionSnapshot snapshot = new DagExecutionSnapshot(workflowId, "resume-user",
                conversationId.toString(), plan, DagWorkflowStatus.WAITING_USER, tasks, Map.of(), now, now);
        try {
            persistence.save(snapshot);
            persistence.saveResumeContext(workflowId, "resume-user", "{\"turn\":\"safe\"}");

            assertThat(persistence.findLatestWaiting("resume-user", conversationId.toString())).contains(snapshot);
            assertThat(persistence.findLatestWaiting("other-user", conversationId.toString())).isEmpty();
            assertThat(persistence.loadResumeContext(workflowId, "resume-user"))
                    .contains("{\"turn\":\"safe\"}");
        } finally {
            repository.deleteById(workflowId.toString());
        }
    }
}
