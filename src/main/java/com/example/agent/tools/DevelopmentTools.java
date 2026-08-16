package com.example.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DevelopmentTools {

    private final Map<String, String> notes = new ConcurrentHashMap<>();

    @Tool("Get the current local date and time.")
    public String currentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool("Create a compact software development checklist for a goal.")
    public String developmentChecklist(String goal) {
        return """
                1. Clarify the user goal and success criteria.
                2. Identify the model, tools, memory, and data sources needed.
                3. Implement the smallest working agent flow.
                4. Add tests or repeatable manual checks.
                5. Log important decisions and failure cases.
                Goal: %s
                """.formatted(goal);
    }

    @Tool("Remember an implementation note by key for this running Java process.")
    public String rememberNote(String key, String value) {
        notes.put(key, value);
        return "Remembered note: " + key;
    }

    @Tool("Recall an implementation note by key from this running Java process.")
    public String recallNote(String key) {
        return notes.getOrDefault(key, "No note found for key: " + key);
    }
}
