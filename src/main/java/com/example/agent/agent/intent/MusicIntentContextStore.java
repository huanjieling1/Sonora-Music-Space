package com.example.agent.agent.intent;

import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.model.bo.ConversationMemoryId;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived conversation intent used only to resolve corrections such as “我是说歌单推荐”. */
@Component
public class MusicIntentContextStore {
    private static final Duration TTL = Duration.ofMinutes(30);
    private final ConcurrentHashMap<ConversationMemoryId, Entry> values = new ConcurrentHashMap<>();

    public Optional<MusicIntentDraft> latest(ConversationMemoryId id) {
        Entry entry = values.get(id);
        if (entry == null) return Optional.empty();
        if (entry.savedAt().plus(TTL).isBefore(Instant.now())) {
            values.remove(id, entry);
            return Optional.empty();
        }
        return Optional.of(entry.intent());
    }

    public void put(ConversationMemoryId id, MusicIntentDraft intent) {
        if (id != null && intent != null) values.put(id, new Entry(intent, Instant.now()));
    }

    private record Entry(MusicIntentDraft intent, Instant savedAt) {
    }
}
