package com.nordia.tasks.api;

import com.nordia.tasks.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

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
}
