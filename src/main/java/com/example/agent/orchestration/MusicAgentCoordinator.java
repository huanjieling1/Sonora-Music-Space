package com.example.agent.orchestration;

import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.main.MusicMainAgent;
import org.springframework.stereotype.Component;

/** Application facade. Supervisory decisions are owned by the main agent. */
@Component
public final class MusicAgentCoordinator {
    private final MusicMainAgent mainAgent;

    public MusicAgentCoordinator(MusicMainAgent mainAgent) {
        this.mainAgent = mainAgent;
    }

    public MusicAgentWorkflowState orchestrate(MusicAgentTurn turn) {
        return mainAgent.run(turn);
    }
}
