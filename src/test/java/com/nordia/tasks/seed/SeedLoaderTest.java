package com.nordia.tasks.seed;

import com.nordia.tasks.util.Timestamps;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SeedLoaderTest {

    private static final String FIRST_TASK = "79ce392b-c44e-48ae-b81c-edd1dd75dc7b";
    private static final String TASK_WITH_UNKNOWN_ASSIGNEE = "b1cb23dd-ab23-46ea-aaa3-f8ca5ac63b5b";

    @Autowired JdbcTemplate jdbc;
    @Autowired SeedLoader seedLoader;

    @Test
    void loadsMembersTasksAndTombstonesAtFirstStart() {
        assertThat(count("SELECT COUNT(*) FROM member")).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM task")).isEqualTo(45);
        assertThat(count("SELECT COUNT(*) FROM task WHERE deleted = TRUE")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT last_seq FROM sync_state WHERE id = 1", Long.class))
                .isEqualTo(45L);
    }

    @Test
    void secondStartDoesNotDuplicateTheSeed() throws Exception {
        assertThat(seedLoader.seedIfEmpty()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM task")).isEqualTo(45);
        assertThat(count("SELECT COUNT(*) FROM member")).isEqualTo(6);
    }

    @Test
    void keepsVersionSequenceAndDateOfTheSeedFile() {
        assertThat(jdbc.queryForObject("SELECT version FROM task WHERE id = ?", Integer.class, FIRST_TASK))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT seq FROM task WHERE id = ?", Long.class, FIRST_TASK))
                .isEqualTo(1L);
        OffsetDateTime updatedAt = jdbc.queryForObject(
                "SELECT updated_at FROM task WHERE id = ?", OffsetDateTime.class, FIRST_TASK);
        assertThat(Timestamps.format(updatedAt)).isEqualTo("2026-10-05T21:50:27.000Z");
    }

    @Test
    void keepsTheUnknownAssigneeOfTheSeedAsIs() {
        assertThat(jdbc.queryForObject(
                "SELECT assignee_id FROM task WHERE id = ?", String.class, TASK_WITH_UNKNOWN_ASSIGNEE))
                .isEqualTo("m-99");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}