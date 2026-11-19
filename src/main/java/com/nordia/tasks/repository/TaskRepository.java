package com.nordia.tasks.repository;

import com.nordia.tasks.model.Task;
import com.nordia.tasks.util.Timestamps;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TaskRepository {

    /** A task with its change number (seq), which the cursor is built from. */
    public record Row(long seq, Task task) {}

    private static final String COLUMNS =
            "id, title, note, status, assignee_id, due_date, version, deleted, updated_at, seq";

    private final JdbcTemplate jdbc;

    public TaskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Task> findById(String id) {
        List<Task> found = jdbc.query("SELECT " + COLUMNS + " FROM task WHERE id = ?",
                (rs, i) -> map(rs), id);
        return found.stream().findFirst();
    }

    /** Tasks changed after {@code since}, oldest change first (tombstones included). */
    public List<Row> findAfter(long since, int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM task WHERE seq > ? ORDER BY seq ASC LIMIT ?",
                (rs, i) -> new Row(rs.getLong("seq"), map(rs)), since, limit);
    }

    private static Task map(ResultSet rs) throws SQLException {
        LocalDate dueDate = rs.getObject("due_date", LocalDate.class);
        return new Task(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("note"),
                rs.getString("status"),
                rs.getString("assignee_id"),
                dueDate == null ? null : dueDate.toString(),
                rs.getInt("version"),
                rs.getBoolean("deleted"),
                Timestamps.format(rs.getObject("updated_at", OffsetDateTime.class)));
    }
}

