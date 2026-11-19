package com.nordia.tasks.service;

import com.nordia.tasks.model.Result;
import com.nordia.tasks.model.Task;
import com.nordia.tasks.model.TaskPage;
import com.nordia.tasks.repository.IdempotencyRepository;
import com.nordia.tasks.repository.IdempotencyRepository.Stored;
import com.nordia.tasks.repository.SyncStateRepository;
import com.nordia.tasks.repository.TaskRepository;
import com.nordia.tasks.repository.TaskRepository.Row;
import com.nordia.tasks.util.Json;
import com.nordia.tasks.util.Timestamps;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final Pattern CURSOR = Pattern.compile("^c(\\d+)$");

    private static final Result NOT_FOUND = Result.error(404, "not_found", "Task not found");
    private static final Result INVALID_JSON =
            Result.error(400, "invalid_json", "The body must be a valid JSON object");

    private final TaskRepository tasks;
    private final SyncStateRepository syncState;
    private final IdempotencyRepository idempotency;
    private final TaskValidator validator;
    private final TransactionTemplate tx;

    public TaskService(TaskRepository tasks, SyncStateRepository syncState,
                       IdempotencyRepository idempotency, TaskValidator validator,
                       TransactionTemplate tx) {
        this.tasks = tasks;
        this.syncState = syncState;
        this.idempotency = idempotency;
        this.validator = validator;
        this.tx = tx;
    }

    // ------------------------------------------------------------------ reads

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
        return tasks.findById(id).map(Result::ok).orElse(NOT_FOUND);
    }

    // ----------------------------------------------------------------- writes

    public Result create(String key, String rawBody) {
        Result refused = checkKey(key);
        if (refused != null) {
            return refused;
        }
        Map<String, Object> body = Json.parseObject(rawBody);
        if (body == null) {
            return INVALID_JSON;
        }
        return idempotent(key, "POST", "/tasks", body, () -> doCreate(body));
    }

    public Result patch(String key, String id, String rawBody) {
        Result refused = checkKey(key);
        if (refused != null) {
            return refused;
        }
        Map<String, Object> body = Json.parseObject(rawBody);
        if (body == null) {
            return INVALID_JSON;
        }
        return idempotent(key, "PATCH", "/tasks/" + id, body, () -> doPatch(id, body));
    }

    public Result delete(String key, String id, String baseVersion) {
        Result refused = checkKey(key);
        if (refused != null) {
            return refused;
        }
        String target = "/tasks/" + id + (baseVersion == null ? "" : "?baseVersion=" + baseVersion);
        return idempotent(key, "DELETE", target, Map.of(), () -> doDelete(id, baseVersion));
    }

    /**
     * The one place where every write goes through. All in one transaction:
     * take the write lock, replay the answer if the key was already used, otherwise
     * run the operation and remember its answer under the key.
     */
    private Result idempotent(String key, String method, String target, Object body,
                              Supplier<Result> operation) {
        String fingerprint = Fingerprint.of(method, target, body);
        return tx.execute(txStatus -> {
            syncState.lockWriters();   // one writer at a time: what makes 409 and replays safe

            Optional<Stored> previous = idempotency.find(key);
            if (previous.isPresent()) {
                Stored stored = previous.get();
                if (!stored.fingerprint().equals(fingerprint)) {
                    return Result.error(422, "idempotency_key_reuse",
                            "This Idempotency-Key was already used for a different request");
                }
                return new Result(stored.status(), stored.body()).replayed();
            }

            Result result = operation.get();
            idempotency.save(key, fingerprint, result);
            return result;
        });
    }

    private Result doCreate(Map<String, Object> body) {
        List<String> errors = validator.forCreate(body);
        if (!errors.isEmpty()) {
            return Result.validation(errors);
        }
        String id = (String) body.get("id");
        Optional<Task> existing = tasks.findById(id);
        if (existing.isPresent()) {
            return Result.error(409, "id_exists", "A task with this id already exists", existing.get());
        }
        Task created = new Task(
                id,
                ((String) body.get("title")).strip(),
                (String) body.get("note"),
                body.containsKey("status") ? (String) body.get("status") : "todo",
                (String) body.get("assigneeId"),
                (String) body.get("dueDate"),
                1, false, now());
        tasks.insert(created, syncState.nextSeq());
        return Result.of(201, created);
    }

    private Result doPatch(String id, Map<String, Object> body) {
        List<String> errors = validator.forPatch(body);
        if (!errors.isEmpty()) {
            return Result.validation(errors);
        }
        Optional<Task> found = tasks.findById(id);
        if (found.isEmpty()) {
            return NOT_FOUND;
        }
        Task current = found.get();
        if (current.deleted()) {
            return Result.error(410, "task_deleted", "This task was deleted", current);
        }
        long baseVersion = ((Number) body.get("baseVersion")).longValue();
        if (baseVersion != current.version()) {
            return Result.error(409, "version_conflict",
                    "The task changed since version " + baseVersion, current);
        }
        if ("done".equals(current.status()) && "todo".equals(body.get("status"))) {
            return Result.error(422, "invalid_transition",
                    "A done task cannot go back to todo (use doing)");
        }

        Task edited = new Task(
                id,
                body.containsKey("title") ? ((String) body.get("title")).strip() : current.title(),
                body.containsKey("note") ? (String) body.get("note") : current.note(),
                body.containsKey("status") ? (String) body.get("status") : current.status(),
                body.containsKey("assigneeId") ? (String) body.get("assigneeId") : current.assigneeId(),
                body.containsKey("dueDate") ? (String) body.get("dueDate") : current.dueDate(),
                current.version(), false, current.updatedAt());
        if (sameFields(current, edited)) {
            return Result.ok(current);   // nothing really changes: no new version
        }
        return commit(current, edited, false);
    }

    private Result doDelete(String id, String baseVersionParam) {
        Integer baseVersion = parseBaseVersion(baseVersionParam);
        if (baseVersion == null) {
            return Result.validation(List.of("baseVersion: integer, the version you started from"));
        }
        Optional<Task> found = tasks.findById(id);
        if (found.isEmpty()) {
            return NOT_FOUND;
        }
        Task current = found.get();
        if (current.deleted()) {
            return Result.ok(current);   // already deleted: nothing to do, whatever the version
        }
        if (baseVersion != current.version()) {
            return Result.error(409, "version_conflict",
                    "The task changed since version " + baseVersion, current);
        }
        return commit(current, current, true);
    }

    /** Writes the next version of the task: version + 1, a new change number, a new date. */
    private Result commit(Task current, Task edited, boolean deleted) {
        Task next = new Task(edited.id(), edited.title(), edited.note(), edited.status(),
                edited.assigneeId(), edited.dueDate(), current.version() + 1, deleted, now());
        if (!tasks.updateIfVersion(next, syncState.nextSeq(), current.version())) {
            // Cannot happen while we hold the write lock: kept as a second line of defence.
            Task latest = tasks.findById(current.id()).orElse(current);
            return Result.error(409, "version_conflict",
                    "The task changed since version " + current.version(), latest);
        }
        return Result.ok(next);
    }

    // ---------------------------------------------------------------- helpers

    private static Result checkKey(String key) {
        if (key == null || key.length() < 8 || key.length() > 64) {
            return Result.error(400, "missing_idempotency_key",
                    "Header Idempotency-Key is required (8 to 64 characters)");
        }
        return null;
    }

    private static boolean sameFields(Task a, Task b) {
        return Objects.equals(a.title(), b.title())
                && Objects.equals(a.note(), b.note())
                && Objects.equals(a.status(), b.status())
                && Objects.equals(a.assigneeId(), b.assigneeId())
                && Objects.equals(a.dueDate(), b.dueDate());
    }

    private static String now() {
        return Timestamps.format(Instant.now().truncatedTo(ChronoUnit.MILLIS));
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

    private static Integer parseBaseVersion(String raw) {
        if (raw == null || !raw.matches("[0-9]+")) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            return value >= 1 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}