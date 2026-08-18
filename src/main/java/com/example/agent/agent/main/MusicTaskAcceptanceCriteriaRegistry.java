package com.example.agent.agent.main;

import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import org.springframework.stereotype.Component;

import java.util.List;

/** Discovers role-owned acceptance policies without a central workflow switch. */
@Component
public final class MusicTaskAcceptanceCriteriaRegistry {
    private final List<MusicTaskAcceptanceCriteriaContributor> contributors;

    public MusicTaskAcceptanceCriteriaRegistry(List<MusicTaskAcceptanceCriteriaContributor> contributors) {
        this.contributors = contributors == null ? List.of() : List.copyOf(contributors);
    }

    public List<String> criteria(MusicWorkflowTaskSpec task, MusicIntentUnderstanding understanding) {
        List<String> values = contributors.stream().filter(value -> value.supports(task))
                .flatMap(value -> value.criteria(task, understanding).stream()).distinct().limit(6).toList();
        return values.isEmpty() ? List.of("任务输出必须满足当前任务目标") : values;
    }

    public static MusicTaskAcceptanceCriteriaRegistry builtIns() {
        return new MusicTaskAcceptanceCriteriaRegistry(List.of(new IntentAcceptanceCriteriaContributor(),
                new ProfileAcceptanceCriteriaContributor(), new EvaluationAcceptanceCriteriaContributor(),
                new ResponseAcceptanceCriteriaContributor(), new ExecutionAcceptanceCriteriaContributor()));
    }
}
