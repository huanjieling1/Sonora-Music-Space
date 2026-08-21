package com.example.agent.orchestration.dag;

import com.example.agent.orchestration.replanning.ReplanProposal;
import com.example.agent.orchestration.replanning.ReplanRequest;

/** Optional extension used by the generic runtime when a task needs a local replacement subgraph. */
public interface ReplanningDagTaskExecutor extends GenericDagTaskExecutor {
    ReplanProposal replan(ReplanRequest request) throws Exception;
}
