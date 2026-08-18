package com.example.agent.skill;

import com.example.agent.agent.contract.MusicAutonomyLevel;
import com.example.agent.agent.contract.MusicSupportContext;
import com.example.agent.tools.MusicAgentTools;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads goal-level skills, validates their tool bindings, and renders their
 * instructions into the LangChain4j system message.
 */
@Component
public class AgentSkillRegistry {
    static final String SKILL_PATTERN = "classpath*:agent-skills/*/SKILL.md";
    static final String BINDINGS_FILE = "tool-bindings.properties";
    private static final List<Class<?>> TOOL_TYPES = List.of(MusicAgentTools.class);

    private final List<AgentSkillDefinition> skills;
    private final Set<String> registeredToolNames;
    private final String systemInstructions;

    public AgentSkillRegistry() {
        this(loadSkills(), discoverToolNames(TOOL_TYPES));
    }

    AgentSkillRegistry(List<AgentSkillDefinition> skills, Set<String> registeredToolNames) {
        this.skills = validateAndSort(skills, registeredToolNames);
        this.registeredToolNames = immutableSet(registeredToolNames);
        this.systemInstructions = renderSystemInstructions(this.skills);
    }

    public List<AgentSkillDefinition> skills() {
        return skills;
    }

    public Set<String> registeredToolNames() {
        return registeredToolNames;
    }

    public Set<String> coveredToolNames() {
        return skills.stream()
                .flatMap(skill -> skill.tools().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String systemInstructions() {
        return systemInstructions;
    }

    public String augmentSystemMessage(String baseMessage) {
        String base = baseMessage == null ? "" : baseMessage.trim();
        if (base.isEmpty()) {
            return systemInstructions;
        }
        return base + "\n\n" + systemInstructions;
    }

    private static List<AgentSkillDefinition> loadSkills() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(SKILL_PATTERN);
            if (resources.length == 0) {
                throw new IllegalStateException("No agent skills found at " + SKILL_PATTERN);
            }
            List<AgentSkillDefinition> loaded = new ArrayList<>();
            for (Resource skillResource : resources) {
                loaded.add(loadSkill(skillResource));
            }
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load agent skills", exception);
        }
    }

    private static AgentSkillDefinition loadSkill(Resource skillResource) throws IOException {
        String markdown;
        try (var input = skillResource.getInputStream()) {
            markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        SkillDocument document = parseSkillDocument(markdown, skillResource.getDescription());

        Resource bindingsResource = skillResource.createRelative(BINDINGS_FILE);
        if (!bindingsResource.exists()) {
            throw new IllegalStateException("Missing " + BINDINGS_FILE + " beside "
                    + skillResource.getDescription());
        }
        Properties bindings = new Properties();
        try (Reader reader = new InputStreamReader(bindingsResource.getInputStream(), StandardCharsets.UTF_8)) {
            bindings.load(reader);
        }
        String id = requiredProperty(bindings, "id", bindingsResource);
        int priority;
        try {
            priority = Integer.parseInt(requiredProperty(bindings, "priority", bindingsResource));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid priority in " + bindingsResource.getDescription(), exception);
        }
        LinkedHashSet<String> tools = Arrays.stream(requiredProperty(bindings, "tools", bindingsResource)
                        .split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> activationTerms = Arrays.stream(
                        bindings.getProperty("triggers", document.name()).split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        AgentSkillSupportAffordance supportAffordance = supportAffordance(bindings, id);
        return new AgentSkillDefinition(id, document.name(), document.description(), priority,
                tools, activationTerms, document.instructions(), skillResource.getDescription(), supportAffordance);
    }

    private static AgentSkillSupportAffordance supportAffordance(Properties bindings, String skillId) {
        boolean proactive = Boolean.parseBoolean(bindings.getProperty("proactive", "false").trim());
        if (!proactive) return AgentSkillSupportAffordance.disabled();
        Set<MusicSupportContext.EmotionalSignal> contexts = enumSet(bindings, "support-contexts",
                MusicSupportContext.EmotionalSignal.class, skillId);
        Set<MusicSupportContext.SupportGoal> goals = enumSet(bindings, "support-goals",
                MusicSupportContext.SupportGoal.class, skillId);
        MusicAutonomyLevel autonomy = enumValue(bindings.getProperty("autonomy", "DISABLED"),
                MusicAutonomyLevel.class, "autonomy", skillId);
        String outputAction = bindings.getProperty("output-action", "").trim();
        int weight;
        try {
            weight = Integer.parseInt(bindings.getProperty("proactive-weight", "50").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid proactive-weight in skill " + skillId, exception);
        }
        if (contexts.isEmpty() || goals.isEmpty() || outputAction.isBlank()
                || autonomy == MusicAutonomyLevel.DISABLED) {
            throw new IllegalStateException("Proactive skill " + skillId
                    + " must declare support-contexts, support-goals, autonomy and output-action");
        }
        return new AgentSkillSupportAffordance(true, contexts, goals, autonomy, outputAction, weight);
    }

    private static <E extends Enum<E>> Set<E> enumSet(Properties properties, String key,
                                                       Class<E> type, String skillId) {
        LinkedHashSet<E> values = new LinkedHashSet<>();
        for (String raw : properties.getProperty(key, "").split(",")) {
            String value = raw.trim();
            if (!value.isEmpty()) values.add(enumValue(value, type, key, skillId));
        }
        return Collections.unmodifiableSet(values);
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type,
                                                    String key, String skillId) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid " + key + " in skill " + skillId + ": " + value,
                    exception);
        }
    }

    static SkillDocument parseSkillDocument(String markdown, String source) {
        if (markdown == null) {
            throw new IllegalStateException("Empty skill document: " + source);
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (!normalized.startsWith("---\n")) {
            throw new IllegalStateException("SKILL.md must start with YAML frontmatter: " + source);
        }
        int metadataEnd = normalized.indexOf("\n---\n", 4);
        if (metadataEnd < 0) {
            throw new IllegalStateException("SKILL.md frontmatter is not closed: " + source);
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        String header = normalized.substring(4, metadataEnd);
        for (String line : header.split("\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                throw new IllegalStateException("Invalid SKILL.md frontmatter line in " + source + ": " + line);
            }
            String key = line.substring(0, separator).trim();
            String value = unquote(line.substring(separator + 1).trim());
            if (!Set.of("name", "description").contains(key)) {
                throw new IllegalStateException("Only name and description are allowed in SKILL.md frontmatter: "
                        + source);
            }
            if (metadata.putIfAbsent(key, value) != null) {
                throw new IllegalStateException("Duplicate SKILL.md metadata key " + key + " in " + source);
            }
        }
        String instructions = normalized.substring(metadataEnd + 5).strip();
        return new SkillDocument(requiredMetadata(metadata, "name", source),
                requiredMetadata(metadata, "description", source), instructions);
    }

    private static List<AgentSkillDefinition> validateAndSort(List<AgentSkillDefinition> sourceSkills,
                                                               Set<String> registeredTools) {
        if (sourceSkills == null || sourceSkills.isEmpty()) {
            throw new IllegalStateException("At least one agent skill is required");
        }
        if (registeredTools == null || registeredTools.isEmpty()) {
            throw new IllegalStateException("At least one LangChain4j tool is required");
        }
        Map<String, AgentSkillDefinition> byId = new LinkedHashMap<>();
        LinkedHashSet<String> coveredTools = new LinkedHashSet<>();
        for (AgentSkillDefinition skill : sourceSkills) {
            if (byId.putIfAbsent(skill.id(), skill) != null) {
                throw new IllegalStateException("Duplicate agent skill id: " + skill.id());
            }
            LinkedHashSet<String> unknownTools = new LinkedHashSet<>(skill.tools());
            unknownTools.removeAll(registeredTools);
            if (!unknownTools.isEmpty()) {
                throw new IllegalStateException("Skill " + skill.id() + " references unknown tools: " + unknownTools);
            }
            coveredTools.addAll(skill.tools());
        }
        LinkedHashSet<String> uncoveredTools = new LinkedHashSet<>(registeredTools);
        uncoveredTools.removeAll(coveredTools);
        if (!uncoveredTools.isEmpty()) {
            throw new IllegalStateException("LangChain4j tools without a skill: " + uncoveredTools);
        }
        return byId.values().stream()
                .sorted(Comparator.comparingInt(AgentSkillDefinition::priority).reversed()
                        .thenComparing(AgentSkillDefinition::id))
                .toList();
    }

    private static Set<String> discoverToolNames(List<Class<?>> toolTypes) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Class<?> toolType : toolTypes) {
            for (Method method : toolType.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Tool.class) && !result.add(method.getName())) {
                    throw new IllegalStateException("Duplicate LangChain4j tool name: " + method.getName());
                }
            }
        }
        return result;
    }

    private static String renderSystemInstructions(List<AgentSkillDefinition> definitions) {
        StringBuilder result = new StringBuilder("""
                # 当前可用 Skill 目录
                选择描述最符合用户当前目标的最小 Skill，并遵循其中的工作流和约束。每个 Skill 的工具列表
                都是该 Skill 的严格白名单。只有请求确实包含多个目标时才能组合多个 Skill。不得虚构工具、
                静默替换 Skill，也不得在缺少工具返回证据时声称工具执行成功。
                """);
        for (AgentSkillDefinition skill : definitions) {
            result.append("\n\n## Skill：").append(skill.id())
                    .append("\n名称：").append(skill.name())
                    .append("\n适用场景：").append(skill.description())
                    .append("\n允许工具：").append(String.join(", ", skill.tools()));
            if (skill.supportAffordance().proactive()) {
                result.append("\n可主动帮助的目标：")
                        .append(skill.supportAffordance().goals())
                        .append("\n主动执行级别：").append(skill.supportAffordance().autonomy());
            }
            result.append("\n\n").append(skill.instructions());
        }
        return result.toString().strip();
    }

    private static String requiredProperty(Properties properties, String key, Resource resource) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing property " + key + " in " + resource.getDescription());
        }
        return value.trim();
    }

    private static String requiredMetadata(Map<String, String> metadata, String key, String source) {
        String value = metadata.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing SKILL.md metadata " + key + " in " + source);
        }
        return value;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Set<String> immutableSet(Set<String> source) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(source));
    }

    record SkillDocument(String name, String description, String instructions) {
    }
}
