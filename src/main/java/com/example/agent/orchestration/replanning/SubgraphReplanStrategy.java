package com.example.agent.orchestration.replanning;

@FunctionalInterface
public interface SubgraphReplanStrategy {
    ReplanProposal propose(ReplanRequest request) throws Exception;
}
