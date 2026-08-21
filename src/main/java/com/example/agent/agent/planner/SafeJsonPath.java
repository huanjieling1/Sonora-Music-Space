package com.example.agent.agent.planner;

import com.example.agent.agent.contract.planning.ValueType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bounded JSONPath reader supporting root, object fields, quoted fields and array indices. */
@Component
public final class SafeJsonPath {
    private static final int MAX_PATH_LENGTH = 512;
    private static final int MAX_SEGMENTS = 32;
    private final ObjectMapper objectMapper;

    public SafeJsonPath(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonPathResult read(Object root, String path) {
        if (path == null || path.isBlank() || path.length() > MAX_PATH_LENGTH || path.charAt(0) != '$') {
            return JsonPathResult.failure("INVALID_JSON_PATH", "JSONPath 必须以 $ 开始且长度有效");
        }
        ParseResult parsed = parse(path);
        if (parsed.error() != null) return JsonPathResult.failure("INVALID_JSON_PATH", parsed.error());
        if (parsed.tokens().size() > MAX_SEGMENTS) {
            return JsonPathResult.failure("JSON_PATH_TOO_DEEP", "JSONPath 层级超过上限 " + MAX_SEGMENTS);
        }
        JsonNode current = objectMapper.valueToTree(root);
        for (Token token : parsed.tokens()) {
            if (token.field() != null) {
                if (current == null || !current.isObject()) {
                    return JsonPathResult.failure("JSON_PATH_TYPE_MISMATCH",
                            "路径字段只能从对象读取：" + token.field());
                }
                current = current.get(token.field());
                if (current == null || current.isMissingNode()) {
                    return JsonPathResult.failure("JSON_PATH_NOT_FOUND", "字段不存在：" + token.field());
                }
            } else {
                if (current == null || !current.isArray()) {
                    return JsonPathResult.failure("JSON_PATH_TYPE_MISMATCH",
                            "数组下标只能用于数组：" + token.index());
                }
                if (token.index() < 0 || token.index() >= current.size()) {
                    return JsonPathResult.failure("JSON_PATH_INDEX_OUT_OF_BOUNDS",
                            "数组下标越界：" + token.index());
                }
                current = current.get(token.index());
            }
        }
        Object value = current == null || current.isNull() ? null
                : freeze(objectMapper.convertValue(current, Object.class));
        return JsonPathResult.success(value, type(current));
    }

    private static ParseResult parse(String path) {
        ArrayList<Token> tokens = new ArrayList<>();
        int cursor = 1;
        while (cursor < path.length()) {
            char marker = path.charAt(cursor);
            if (marker == '.') {
                int start = ++cursor;
                while (cursor < path.length() && path.charAt(cursor) != '.' && path.charAt(cursor) != '[') cursor++;
                if (start == cursor) return new ParseResult(List.of(), "点号后缺少字段名");
                tokens.add(Token.field(path.substring(start, cursor)));
                continue;
            }
            if (marker != '[') return new ParseResult(List.of(), "不支持的 JSONPath 语法位置：" + cursor);
            cursor++;
            if (cursor >= path.length()) return new ParseResult(List.of(), "方括号未闭合");
            char first = path.charAt(cursor);
            if (first == '\'' || first == '"') {
                char quote = first;
                int start = ++cursor;
                StringBuilder key = new StringBuilder();
                boolean closed = false;
                while (cursor < path.length()) {
                    char value = path.charAt(cursor++);
                    if (value == '\\' && cursor < path.length()) {
                        key.append(path.charAt(cursor++));
                    } else if (value == quote) {
                        closed = true;
                        break;
                    } else {
                        key.append(value);
                    }
                }
                if (!closed || cursor >= path.length() || path.charAt(cursor) != ']') {
                    return new ParseResult(List.of(), "引号字段或方括号未闭合");
                }
                cursor++;
                if (key.isEmpty() && start == cursor) return new ParseResult(List.of(), "字段名不能为空");
                tokens.add(Token.field(key.toString()));
            } else {
                int start = cursor;
                while (cursor < path.length() && Character.isDigit(path.charAt(cursor))) cursor++;
                if (start == cursor || cursor >= path.length() || path.charAt(cursor) != ']') {
                    return new ParseResult(List.of(), "数组下标必须是非负整数");
                }
                int index;
                try {
                    index = Integer.parseInt(path.substring(start, cursor));
                } catch (NumberFormatException error) {
                    return new ParseResult(List.of(), "数组下标超出整数范围");
                }
                cursor++;
                tokens.add(Token.index(index));
            }
        }
        return new ParseResult(List.copyOf(tokens), null);
    }

    private static ValueType type(JsonNode node) {
        if (node == null || node.isNull()) return ValueType.ANY;
        if (node.isTextual()) return ValueType.STRING;
        if (node.isIntegralNumber()) return ValueType.INTEGER;
        if (node.isFloatingPointNumber()) return ValueType.DECIMAL;
        if (node.isBoolean()) return ValueType.BOOLEAN;
        if (node.isArray()) return ValueType.ARRAY;
        if (node.isObject()) return ValueType.OBJECT;
        return ValueType.ANY;
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), freeze(item)));
            return Map.copyOf(result);
        }
        if (value instanceof Iterable<?> iterable) {
            ArrayList<Object> result = new ArrayList<>();
            iterable.forEach(item -> result.add(freeze(item)));
            return List.copyOf(result);
        }
        return value;
    }

    private record Token(String field, int index) {
        static Token field(String field) { return new Token(field, -1); }
        static Token index(int index) { return new Token(null, index); }
    }

    private record ParseResult(List<Token> tokens, String error) {}

    public record JsonPathResult(boolean found, Object value, ValueType valueType,
                                 String errorCode, String message) {
        static JsonPathResult success(Object value, ValueType type) {
            return new JsonPathResult(true, value, type, "", "");
        }
        static JsonPathResult failure(String code, String message) {
            return new JsonPathResult(false, null, ValueType.ANY, code, message);
        }
    }
}
