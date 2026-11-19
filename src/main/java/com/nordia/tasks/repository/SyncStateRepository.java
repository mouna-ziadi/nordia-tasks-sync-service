package com.nordia.tasks.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SyncStateRepository {

    private final JdbcTemplate jdbc;

    public SyncStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Takes the write lock: until the transaction ends, no other writer gets past this line. */
    public void lockWriters() {
        jdbc.queryForObject("SELECT last_seq FROM sync_state WHERE id = 1 FOR UPDATE", Long.class);
    }

    /** Allocates the next change number. Only call while holding the lock. */
    public long nextSeq() {
        jdbc.update("UPDATE sync_state SET last_seq = last_seq + 1 WHERE id = 1");
        return jdbc.queryForObject("SELECT last_seq FROM sync_state WHERE id = 1", Long.class);
    }
}