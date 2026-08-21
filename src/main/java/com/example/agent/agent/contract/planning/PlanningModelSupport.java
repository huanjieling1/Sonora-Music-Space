package com.example.agent.agent.contract.planning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class PlanningModelSupport {
    private PlanningModelSupport() {}

    static String requiredText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.strip();
    }

    static String text(String value) {
        return value == null ? "" : value.strip();
    }

    static List<String> strings(List<String> source) {
        if (source == null) return List.of();
        return source.stream().filter(Objects::nonNull).map(String::strip)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }

    static <T> List<T> list(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }

    static <T> Map<String, T> map(Map<String, T> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(requiredText(key, "映射键不能为空"), value));
        return Map.copyOf(result);
    }

    static List<List<String>> stages(List<List<String>> source) {
        if (source == null) return List.of();
        return source.stream().map(PlanningModelSupport::strings).map(List::copyOf).toList();
    }

    static Object immutableJsonValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> source) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), immutableJsonValue(item)));
            return Map.copyOf(result);
        }
        if (value instanceof Iterable<?> source) {
            ArrayList<Object> result = new ArrayList<>();
            source.forEach(item -> result.add(immutableJsonValue(item)));
            return List.copyOf(result);
        }
        throw new IllegalArgumentException("字面量只支持 JSON 基础值、对象和数组");
    }
}
