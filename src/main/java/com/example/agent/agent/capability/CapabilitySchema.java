package com.example.agent.agent.capability;

import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned object schema used for static planner input/output type checking. */
public record CapabilitySchema(
        String id,
        Map<String, CapabilityFieldSchema> fields,
        boolean additionalProperties
) {
    public CapabilitySchema {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("能力 Schema 标识不能为空");
        id = id.strip();
        LinkedHashMap<String, CapabilityFieldSchema> normalized = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((name, field) -> {
                if (name == null || name.isBlank()) throw new IllegalArgumentException("Schema 字段名不能为空");
                if (field == null) throw new IllegalArgumentException("Schema 字段定义不能为空：" + name);
                String key = name.strip();
                if (normalized.putIfAbsent(key, field) != null) {
                    throw new IllegalArgumentException("Schema 字段重复：" + key);
                }
            });
        }
        fields = Map.copyOf(normalized);
    }

    public static CapabilitySchema empty(String id) {
        return new CapabilitySchema(id, Map.of(), false);
    }

    public static CapabilitySchema object(String id, Map<String, CapabilityFieldSchema> fields) {
        return new CapabilitySchema(id, fields, false);
    }
}
