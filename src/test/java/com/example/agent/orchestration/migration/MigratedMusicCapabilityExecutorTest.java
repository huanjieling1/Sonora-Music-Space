package com.example.agent.orchestration.migration;

import com.example.agent.agent.capability.AgentCapabilityRegistry;
import com.example.agent.agent.capability.MusicPlanningCapabilityContributor;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.agent.contract.planning.GoalNode;
import com.example.agent.agent.contract.planning.GoalOperation;
import com.example.agent.agent.contract.planning.GoalTargetType;
import com.example.agent.agent.contract.planning.PlanTask;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import com.example.agent.agent.profile.FavoriteArtistResolver;
import com.example.agent.orchestration.dag.DagTaskExecutionRequest;
import com.example.agent.orchestration.dag.DagTaskOutcome;
import com.example.agent.skill.AgentSkillRegistry;
import com.example.agent.tools.AgentActionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigratedMusicCapabilityExecutorTest {
    @Test
    void executesProfileAndEntityResolutionAsTypedCapabilitiesWithIdentityBinding() {
        AgentCapabilityRegistry capabilityRegistry = new AgentCapabilityRegistry(new AgentSkillRegistry(),
                List.of(new MusicPlanningCapabilityContributor()));
        MigratedMusicExecutionContextRegistry contexts = new MigratedMusicExecutionContextRegistry();
        MigratedMusicCapabilityExecutor executor = new MigratedMusicCapabilityExecutor(capabilityRegistry,
                contexts, null, new FavoriteArtistResolver(), null, new AgentActionContext(),
                new ObjectMapper().findAndRegisterModules());
        UUID workflowId = UUID.randomUUID();
        MusicAgentTurn turn = new MusicAgentTurn(7, UUID.randomUUID(), "分析画像并找出偏好歌手");
        UserTasteContext taste = new UserTasteContext("MATURE", "成熟", true, 8, 3, 1000, 0.8,
                List.of(), List.of(), List.of(), List.of(),
                List.of(new UserTasteContext.RankedItem("Mili", "", 8, "artist-play:mili")),
                List.of(), List.of());
        UserGoalGraph graph = new UserGoalGraph("1.0", UUID.randomUUID(), turn.request(),
                List.of(new GoalNode("profile", "画像", GoalOperation.ANALYZE, GoalTargetType.PROFILE,
                        Map.of(), List.of(), List.of(), List.of(), false)), List.of());
        contexts.register(workflowId, new MigratedMusicExecutionContextRegistry.Context(
                turn, taste, MusicTurnPlan.none(), graph));

        PlanTask profileTask = task("profile-task", "profile.music.read", "profile");
        DagTaskOutcome profile = executor.execute(new DagTaskExecutionRequest(workflowId, "7", profileTask,
                Map.of(), 1, ""));
        PlanTask resolveTask = task("resolve-task", "profile.artist.resolve", "profile");
        DagTaskOutcome resolved = executor.execute(new DagTaskExecutionRequest(workflowId, "7", resolveTask,
                Map.of("profile", profile.result().output()), 1, ""));

        assertThat(profile.kind()).isEqualTo(DagTaskOutcome.Kind.SUCCESS);
        assertThat(profile.result().output()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("stage", "MATURE");
        assertThat(resolved.kind()).isEqualTo(DagTaskOutcome.Kind.SUCCESS);
        assertThat(resolved.result().entities()).singleElement()
                .satisfies(entity -> assertThat(entity.canonicalName()).isEqualTo("Mili"));
        assertThatThrownBy(() -> executor.execute(new DagTaskExecutionRequest(workflowId, "8", profileTask,
                Map.of(), 1, ""))).isInstanceOf(SecurityException.class);
    }

    private static PlanTask task(String id, String capability, String goalId) {
        return new PlanTask(id, id, capability, List.of(goalId), Map.of(), List.of(), List.of(), 1);
    }
}
