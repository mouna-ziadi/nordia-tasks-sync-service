package com.nordia.tasks.util;

import tools.jackson.databind.json.JsonMapper;

public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Json() {}

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}