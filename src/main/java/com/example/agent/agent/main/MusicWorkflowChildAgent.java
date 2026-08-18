package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicChildAgentDescriptor;
import com.example.agent.agent.contract.MusicTaskInvocation;
import com.example.agent.agent.contract.MusicTaskResult;

/** Executable child-agent port selected dynamically by declared capability. */
public interface MusicWorkflowChildAgent {
    MusicChildAgentDescriptor descriptor();

    MusicTaskResult execute(MusicTaskInvocation invocation);
}
