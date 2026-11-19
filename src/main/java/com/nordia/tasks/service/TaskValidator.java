package com.nordia.tasks.service;

import com.nordia.tasks.repository.MemberRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Checks the JSON received. The messages and their order follow the provided mock server. */
@Component
class TaskValidator {

    private static final Set<String> STATUSES = Set.of("todo", "doing", "done");
    private static final List<String> FIELDS = List.of("title", "note", "status", "assigneeId", "dueDate");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final MemberRepository members;

    TaskValidator(MemberRepository members) {
        this.members = members;
    }

    List<String> forCreate(Map<String, Object> body) {
        List<String> errors = fieldErrors(body, true);
        if (!(body.get("id") instanceof String id) || !UUID.matcher(id).matches()) {
            errors.add("id: a UUID chosen by the client");
        }
        addUnknown(errors, body, "id");
        return errors;
    }

    List<String> forPatch(Map<String, Object> body) {
        List<String> errors = fieldErrors(body, false);
        if (!isPositiveInteger(body.get("baseVersion"))) {
            errors.add("baseVersion: integer, the version you started from");
        }
        addUnknown(errors, body, "baseVersion");
        if (FIELDS.stream().noneMatch(body::containsKey)) {
            errors.add("nothing to change");
        }
        return errors;
    }

    private List<String> fieldErrors(Map<String, Object> f, boolean creating) {
        List<String> errors = new ArrayList<>();
        if ((creating || f.containsKey("title")) && !isTitle(f.get("title"))) {
            errors.add("title: 1 to 120 characters");
        }
        if (f.containsKey("note") && f.get("note") != null
                && !(f.get("note") instanceof String note && note.length() <= 500)) {
            errors.add("note: null or at most 500 characters");
        }
        if (f.containsKey("status") && !(f.get("status") instanceof String s && STATUSES.contains(s))) {
            errors.add("status: one of todo, doing, done");
        }
        if (f.containsKey("assigneeId") && f.get("assigneeId") != null && !isMember(f.get("assigneeId"))) {
            errors.add("assigneeId: null or the id of a member");
        }
        if (f.containsKey("dueDate") && f.get("dueDate") != null && !isCalendarDate(f.get("dueDate"))) {
            errors.add("dueDate: null or a calendar date YYYY-MM-DD");
        }
        return errors;
    }

    private static void addUnknown(List<String> errors, Map<String, Object> body, String allowedExtra) {
        List<String> unknown = body.keySet().stream()
                .filter(k -> !k.equals(allowedExtra) && !FIELDS.contains(k))
                .toList();
        if (!unknown.isEmpty()) {
            errors.add("unknown fields: " + String.join(", ", unknown));
        }
    }

    private static boolean isTitle(Object value) {
        return value instanceof String s && !s.strip().isEmpty() && s.strip().length() <= 120;
    }

    private static boolean isPositiveInteger(Object value) {
        return (value instanceof Integer || value instanceof Long) && ((Number) value).longValue() >= 1;
    }

    private boolean isMember(Object value) {
        return value instanceof String id && members.findAll().stream().anyMatch(m -> m.id().equals(id));
    }

    private static boolean isCalendarDate(Object value) {
        if (!(value instanceof String s) || !DATE.matcher(s).matches()) {
            return false;
        }
        try {
            return LocalDate.parse(s).toString().equals(s);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}