package com.nordia.tasks.seed;

import com.nordia.tasks.model.Member;
import com.nordia.tasks.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads members.json and tasks.json at the very first start only. */
@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SeedLoader(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        seedIfEmpty();
    }

    /** @return true if the seed was loaded, false if the database was already seeded. */
    public boolean seedIfEmpty() throws IOException {
        // Files are read before the transaction: nothing can fail half-way through the inserts.
        Member[] members = read("seed/members.json", Member[].class);
        Task[] tasks = read("seed/tasks.json", Task[].class);

        boolean loaded = Boolean.TRUE.equals(tx.execute(status -> {
            Integer alreadySeeded = jdbc.queryForObject("SELECT COUNT(*) FROM sync_state", Integer.class);
            if (alreadySeeded != null && alreadySeeded > 0) {
                return false;
            }
            Set<String> memberIds = Arrays.stream(members).map(Member::id).collect(Collectors.toSet());
            for (Member m : members) {
                jdbc.update("INSERT INTO member (id, name) VALUES (?, ?)", m.id(), m.name());
            }
            long seq = 0;
            for (Task t : tasks) {
                seq++; // same order as the file, like the mock server
                if (t.assigneeId() != null && !memberIds.contains(t.assigneeId())) {
                    log.warn("Seed task {} has unknown assignee '{}': loaded as-is", t.id(), t.assigneeId());
                }
                jdbc.update("""
                        INSERT INTO task (id, title, note, status, assignee_id, due_date,
                                          version, deleted, updated_at, seq)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                        t.id(), t.title(), t.note(), t.status(), t.assigneeId(),
                        t.dueDate() == null ? null : LocalDate.parse(t.dueDate()),
                        t.version(), t.deleted(),
                        OffsetDateTime.ofInstant(Instant.parse(t.updatedAt()), ZoneOffset.UTC),
                        seq);
            }
            // The marker row is written in the same transaction as the data.
            jdbc.update("INSERT INTO sync_state (id, last_seq) VALUES (1, ?)", seq);
            return true;
        }));

        if (loaded) {
            log.info("Seed loaded: {} members, {} tasks", members.length, tasks.length);
        } else {
            log.info("Database already seeded, nothing to do");
        }
        return loaded;
    }

    private static <T> T read(String path, Class<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return JSON.readValue(in, type);
        }
    }
}
