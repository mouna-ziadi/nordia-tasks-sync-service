package com.nordia.tasks.api;

import com.nordia.tasks.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping("/tasks")
    public ResponseEntity<String> list(
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "limit", required = false) String limit) {
        return Responses.of(service.list(since, limit));
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<String> get(@PathVariable("id") String id) {
        return Responses.of(service.get(id));
    }

    // The body is read as raw text: the service parses it, so that an absent field
    // and a field set to null stay different, and so that bad JSON gets our error format.

    @PostMapping("/tasks")
    public ResponseEntity<String> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
            @RequestBody(required = false) String body) {
        return Responses.of(service.create(key, body));
    }

    @PatchMapping("/tasks/{id}")
    public ResponseEntity<String> patch(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
            @PathVariable("id") String id,
            @RequestBody(required = false) String body) {
        return Responses.of(service.patch(key, id, body));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<String> delete(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key,
            @PathVariable("id") String id,
            @RequestParam(name = "baseVersion", required = false) String baseVersion) {
        return Responses.of(service.delete(key, id, baseVersion));
    }
}