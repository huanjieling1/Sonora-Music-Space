package com.example.agent.agent.goal;

import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.planning.UserGoalGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Public multi-goal understanding facade: semantic proposal plus deterministic grounding and fallback. */
@Component
public final class MusicGoalDecomposer {
    private final MusicGoalDraftInterpreter interpreter;
    private final DeterministicMusicGoalParser deterministicParser;
    private final MusicGoalGraphCorrector corrector;
    private final MusicGoalCompatibilityAdapter compatibilityAdapter;

    public MusicGoalDecomposer() {
        this(request -> java.util.Optional.empty(), new DeterministicMusicGoalParser(),
                new MusicGoalGraphCorrector(), new MusicGoalCompatibilityAdapter());
    }

    @Autowired
    public MusicGoalDecomposer(MusicGoalDraftInterpreter interpreter,
                               DeterministicMusicGoalParser deterministicParser,
                               MusicGoalGraphCorrector corrector,
                               MusicGoalCompatibilityAdapter compatibilityAdapter) {
        this.interpreter = interpreter;
        this.deterministicParser = deterministicParser;
        this.corrector = corrector;
        this.compatibilityAdapter = compatibilityAdapter;
    }

    public UserGoalGraph decompose(String request) {
        UserGoalGraph fallback = deterministicParser.parse(request);
        UserGoalGraph candidate = interpreter == null ? null : interpreter.decompose(request).orElse(null);
        return corrector.correct(request, candidate, fallback);
    }

    public UserGoalGraph fromLegacyIntent(String request, MusicIntentDraft intent) {
        return compatibilityAdapter.fromIntent(request, intent);
    }
}
