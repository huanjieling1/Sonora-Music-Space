package com.example.agent.service;

import com.example.agent.model.bo.MusicSearchIntent;
import com.example.agent.model.bo.MusicUnderstandingBo;

/**
 * Extracts the single provider query used by Agent chat searches.
 *
 * <p>This boundary deliberately does not expose query expansion. QQ Music owns
 * recall, spelling tolerance and result ordering for the Agent search path.</p>
 */
public interface MusicKeywordExtractor {
    ExtractedKeyword extract(String description);

    record ExtractedKeyword(String keyword, MusicSearchIntent intent,
                            MusicUnderstandingBo understanding) {
    }
}
