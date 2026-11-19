package com.nordia.tasks.repository;

import com.nordia.tasks.model.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class IdempotencyRepository {

    public record Stored(String fingerprint, int status, String body) {}

    private final JdbcTemplate jdbc;

    public IdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Stored> find(String key) {
        return jdbc.query(
                "SELECT fingerprint, http_status, body FROM idempotency WHERE idem_key = ?",
                (rs, i) -> new Stored(rs.getString("fingerprint"), rs.getInt("http_status"), rs.getString("body")),
                key).stream().findFirst();
    }

    public void save(String key, String fingerprint, Result result) {
        jdbc.update("""
                INSERT INTO idempotency (idem_key, fingerprint, http_status, body, created_at)
                VALUES (?, ?, ?, ?, ?)""",
                key, fingerprint, result.status(), result.body(), LocalDateTime.now(ZoneOffset.UTC));
    }
}