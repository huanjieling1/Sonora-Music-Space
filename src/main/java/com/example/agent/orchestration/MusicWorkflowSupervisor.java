package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicTaskEvaluation;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.model.bo.AgentActionType;
import com.example.agent.agent.main.MusicTaskCorrectionAgent;
import com.example.agent.agent.main.MusicTaskPlanningAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Creates bounded workflow runs and owns retry decisions; it never invokes business tools itself. */
@Component
public class MusicWorkflowSupervisor {
    private final MusicTaskPlanningAgent planningAgent;
    private final MusicTaskEvaluator evaluator;
    private final MusicTaskCorrectionAgent correctionAgent;

    public MusicWorkflowSupervisor(MusicWorkflowPlanner planner, MusicTaskEvaluator evaluator) {
        this(new MusicTaskPlanningAgent(planner, new MusicPlanValidator()), evaluator,
                new MusicTaskCorrectionAgent());
    }

    @Autowired
    public MusicWorkflowSupervisor(MusicTaskPlanningAgent planningAgent, MusicTaskEvaluator evaluator,
                                   MusicTaskCorrectionAgent correctionAgent) {
        this.planningAgent = planningAgent;
        this.evaluator = evaluator;
        this.correctionAgent = correctionAgent;
    }

    public MusicWorkflowRun start(MusicAgentTurn turn, MusicAgentRoute route, boolean usesProfile) {
        return start(turn, MusicIntentUnderstanding.routed(route,
                com.example.agent.agent.contract.MusicIntentDraft.unknown()), route, usesProfile);
    }

    public MusicWorkflowRun start(MusicAgentTurn turn, MusicIntentUnderstanding understanding,
                                  MusicAgentRoute route, boolean usesProfile) {
        MusicWorkflowRun run = new MusicWorkflowRun(planningAgent.plan(turn, understanding, route, usesProfile));
        run.start("intent");
        run.complete("intent");
        return run;
    }

    public MusicTaskEvaluation evaluate(MusicExecutionResult result) {
        return evaluator.evaluate(result);
    }

    public MusicTaskEvaluation evaluate(MusicExecutionResult result, MusicIntentUnderstanding understanding) {
        return evaluator.evaluate(result, understanding);
    }

    public MusicTaskEvaluation evaluateSupport(MusicExecutionResult result, AgentActionType expectedEvidence) {
        return evaluator.evaluateSupport(result, expectedEvidence);
    }

    public MusicAgentTurn correct(MusicAgentTurn turn, MusicTaskEvaluation evaluation, int nextAttempt) {
        return correctionAgent.correct(turn, evaluation, nextAttempt);
    }
}
