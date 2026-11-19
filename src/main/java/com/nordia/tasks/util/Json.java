package com.nordia.tasks.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    /** Parses a JSON object. Blank text means {}. Returns null when the text is not a JSON object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object value = MAPPER.readValue(raw, Object.class);
            return value instanceof Map ? (Map<String, Object>) value : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    /** Same content, same text: object keys are sorted, so their order never matters. */
    public static String canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), v));
            return sorted.entrySet().stream()
                    .map(e -> write(e.getKey()) + ":" + canonical(e.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Json::canonical).collect(Collectors.joining(",", "[", "]"));
        }
        return write(value);
    }
}