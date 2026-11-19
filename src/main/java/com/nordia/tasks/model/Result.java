package com.nordia.tasks.model;

import com.nordia.tasks.util.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outcome of an operation: HTTP status + JSON body. Business errors are values, not exceptions. */
public record Result(int status, String body, boolean replay) {

    public Result(int status, String body) {
        this(status, body, false);
    }

    public static Result of(int status, Object payload) {
        return new Result(status, Json.write(payload));
    }

    public static Result ok(Object payload) {
        return of(200, payload);
    }

    public static Result error(int status, String code, String message) {
        return of(status, errorBody(code, message));
    }

    /** 409 and 410 answers carry the task as it is now. */
    public static Result error(int status, String code, String message, Task current) {
        Map<String, Object> body = errorBody(code, message);
        body.put("current", current);
        return of(status, body);
    }

    public static Result validation(List<String> errors) {
        Map<String, Object> body = errorBody("validation_error", String.join("; ", errors));
        body.put("details", errors);
        return of(422, body);
    }

    /** The same answer, flagged as a replay of an earlier one (header Idempotent-Replay). */
    public Result replayed() {
        return new Result(status, body, true);
    }

    private static Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("error", message);
        return body;
    }
}