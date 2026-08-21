package com.example.agent.agent.evaluation;

import java.util.List;

/** A final user-visible assertion and the task evidence identifiers supporting it. */
public record GroundedClaim(String text, List<String> evidenceIds) {
    public GroundedClaim {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("最终结论文本不能为空");
        text = text.strip();
        evidenceIds = evidenceIds == null ? List.of() : evidenceIds.stream()
                .filter(value -> value != null && !value.isBlank()).map(String::strip).distinct().toList();
    }
}
