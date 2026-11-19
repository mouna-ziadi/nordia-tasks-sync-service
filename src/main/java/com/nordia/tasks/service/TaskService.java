package com.nordia.tasks.service;

import com.nordia.tasks.model.Result;
import com.nordia.tasks.model.Task;
import com.nordia.tasks.model.TaskPage;
import com.nordia.tasks.repository.TaskRepository;
import com.nordia.tasks.repository.TaskRepository.Row;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final Pattern CURSOR = Pattern.compile("^c(\\d+)$");

    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    public Result list(String since, String limitParam) {
        String sinceCursor = (since == null || since.isEmpty()) ? null : since;
        long sinceSeq = 0;
        if (sinceCursor != null) {
            Long parsed = parseCursor(sinceCursor);
            if (parsed == null) {
                return Result.error(400, "invalid_cursor", "Unknown cursor");
            }
            sinceSeq = parsed;
        }

        int limit = DEFAULT_LIMIT;
        if (limitParam != null) {
            Integer parsed = parseLimit(limitParam);
            if (parsed == null) {
                return Result.error(422, "validation_error", "limit: integer from 1 to 50");
            }
            limit = parsed;
        }

        // One extra row tells us whether there is a next page, without a second query.
        List<Row> rows = tasks.findAfter(sinceSeq, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<Row> page = hasMore ? rows.subList(0, limit) : rows;
        String nextCursor = page.isEmpty() ? sinceCursor : "c" + page.get(page.size() - 1).seq();
        List<Task> items = page.stream().map(Row::task).toList();
        return Result.ok(new TaskPage(items, nextCursor, hasMore));
    }

    public Result get(String id) {
        return tasks.findById(id)
                .map(Result::ok)
                .orElseGet(() -> Result.error(404, "not_found", "Task not found"));
    }

    private static Long parseCursor(String cursor) {
        Matcher m = CURSOR.matcher(cursor);
        if (!m.matches()) {
            return null;
        }
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseLimit(String raw) {
        if (!raw.matches("[0-9]+")) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            return (value >= 1 && value <= MAX_LIMIT) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}