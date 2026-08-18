package com.example.agent.agent.capability;

import com.example.agent.skill.AgentSkillDefinition;
import com.example.agent.skill.AgentSkillRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Read-only runtime capability snapshot. Loaded Skills and feature contributors
 * are the facts; this class contains no product capability list.
 */
@Component
public class AgentCapabilityRegistry {
    private static final String REGEX_PREFIX = "regex:";

    private final List<AgentCapabilityDefinition> capabilities;
    private final Set<String> toolNames;

    /** Test/standalone convenience; Spring uses the injected constructor below. */
    public AgentCapabilityRegistry() {
        this(new AgentSkillRegistry(), List.of());
    }

    public AgentCapabilityRegistry(AgentSkillRegistry skillRegistry) {
        this(skillRegistry, List.of());
    }

    @Autowired
    public AgentCapabilityRegistry(AgentSkillRegistry skillRegistry,
                                   List<AgentCapabilityContributor> contributors) {
        List<AgentCapabilityDefinition> discovered = new ArrayList<>();
        for (AgentSkillDefinition skill : skillRegistry.skills()) {
            discovered.add(new AgentCapabilityDefinition(
                    skill.id(), skill.name(), skill.description(), skill.tools(),
                    skill.activationTerms(), "skill:" + skill.id()));
        }
        if (contributors != null) {
            contributors.stream()
                    .filter(java.util.Objects::nonNull)
                    .flatMap(value -> value.capabilities().stream())
                    .forEach(discovered::add);
        }
        this.capabilities = validate(discovered, skillRegistry.registeredToolNames());
        this.toolNames = capabilities.stream().flatMap(value -> value.tools().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<AgentCapabilityDefinition> capabilities() {
        return capabilities;
    }

    public Set<String> toolNames() {
        return toolNames;
    }

    public boolean supportsTool(String toolName) {
        return toolName != null && toolNames.contains(toolName);
    }

    /** Returns true only when a currently loaded capability declares a matching activation term. */
    public boolean matches(String message) {
        String normalized = message == null ? "" : message.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return false;
        return capabilities.stream().flatMap(value -> value.activationTerms().stream())
                .anyMatch(term -> matchesTerm(normalized, term));
    }

    public String capabilityAnswer() {
        String items = capabilities.stream()
                .map(value -> "- " + value.name())
                .collect(Collectors.joining("\n"));
        return "我会根据当前实际加载的能力来回答。现在已加载 " + capabilities.size() + " 项：\n" + items;
    }

    public String outOfScopeAnswer() {
        return "这个请求超出了当前已加载的能力范围。目前可用：" + capabilityNames() + "。";
    }

    public String clarificationAnswer() {
        return "我还不能确定要调用哪项能力。目前已加载：" + capabilityNames() + "。请再具体描述一下你的目标。";
    }

    public String unverifiedActionAnswer() {
        return "这次操作没有产生可验证的执行结果，我不会把它说成已经完成。请重新发起请求。";
    }

    private String capabilityNames() {
        return capabilities.stream().map(AgentCapabilityDefinition::name).collect(Collectors.joining("、"));
    }

    private static boolean matchesTerm(String normalizedMessage, String rawTerm) {
        String term = rawTerm == null ? "" : rawTerm.strip();
        if (term.regionMatches(true, 0, REGEX_PREFIX, 0, REGEX_PREFIX.length())) {
            return Pattern.compile(term.substring(REGEX_PREFIX.length()),
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(normalizedMessage).find();
        }
        return !term.isEmpty() && normalizedMessage.contains(term.toLowerCase(Locale.ROOT));
    }

    private static List<AgentCapabilityDefinition> validate(List<AgentCapabilityDefinition> discovered,
                                                             Set<String> registeredTools) {
        if (discovered.isEmpty()) {
            throw new IllegalStateException("At least one runtime capability is required");
        }
        Map<String, AgentCapabilityDefinition> byId = new LinkedHashMap<>();
        LinkedHashSet<String> knownTools = new LinkedHashSet<>(registeredTools);
        for (AgentCapabilityDefinition capability : discovered) {
            if (byId.putIfAbsent(capability.id(), capability) != null) {
                throw new IllegalStateException("Duplicate runtime capability id: " + capability.id());
            }
            LinkedHashSet<String> unknown = new LinkedHashSet<>(capability.tools());
            unknown.removeAll(knownTools);
            if (!unknown.isEmpty()) {
                throw new IllegalStateException("Capability " + capability.id()
                        + " references tools that are not actually registered: " + unknown);
            }
            for (String term : capability.activationTerms()) {
                if (term.regionMatches(true, 0, REGEX_PREFIX, 0, REGEX_PREFIX.length())) {
                    Pattern.compile(term.substring(REGEX_PREFIX.length()),
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                }
            }
        }
        return List.copyOf(byId.values());
    }
}
