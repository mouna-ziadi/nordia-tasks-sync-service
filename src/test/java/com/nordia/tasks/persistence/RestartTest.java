package com.nordia.tasks.persistence;

import com.nordia.tasks.TasksApplication;
import com.nordia.tasks.model.Result;
import com.nordia.tasks.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real file-based H2, two successive starts of the whole application. */
class RestartTest {

    @Test
    void seedIsNotLoadedAgainAfterARestart(@TempDir Path dir) {
        try (var first = start(dir)) {
            assertThat(count(first, "SELECT COUNT(*) FROM task")).isEqualTo(45);
        }
        try (var second = start(dir)) {
            assertThat(count(second, "SELECT COUNT(*) FROM task")).isEqualTo(45);
            assertThat(count(second, "SELECT COUNT(*) FROM member")).isEqualTo(6);
        }
    }

    @Test
    void idempotencyKeysAndTheChangeCounterSurviveARestart(@TempDir Path dir) {
        String id = UUID.randomUUID().toString();
        String key = UUID.randomUUID().toString();
        String json = "{\"id\":\"" + id + "\",\"title\":\"Before the restart\"}";

        Result original;
        try (var first = start(dir)) {
            original = first.getBean(TaskService.class).create(key, json);
            assertThat(original.status()).isEqualTo(201);
            assertThat(original.replay()).isFalse();
        }

        try (var second = start(dir)) {
            TaskService service = second.getBean(TaskService.class);

            // The same operation after the restart: the original answer, no second task.
            Result replay = service.create(key, json);
            assertThat(replay.status()).isEqualTo(201);
            assertThat(replay.body()).isEqualTo(original.body());
            assertThat(replay.replay()).isTrue();
            assertThat(count(second, "SELECT COUNT(*) FROM task WHERE id = ?", id)).isEqualTo(1);
            assertThat(count(second, "SELECT COUNT(*) FROM task")).isEqualTo(46);

            // The change counter went on from where it stopped (45 seeded, 46 before the restart).
            String otherId = UUID.randomUUID().toString();
            Result next = service.create(UUID.randomUUID().toString(),
                    "{\"id\":\"" + otherId + "\",\"title\":\"After the restart\"}");
            assertThat(next.status()).isEqualTo(201);
            assertThat(second.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT seq FROM task WHERE id = ?", Long.class, otherId)).isEqualTo(47L);
        }
    }

    private static ConfigurableApplicationContext start(Path dir) {
        SpringApplication app = new SpringApplication(TasksApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        String file = dir.resolve("nordia").toAbsolutePath().toString().replace('\\', '/');
        return app.run(
                "--spring.datasource.url=jdbc:h2:file:" + file + ";LOCK_TIMEOUT=10000",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--spring.sql.init.mode=always",
                "--spring.main.banner-mode=off");
    }

    private static int count(ConfigurableApplicationContext context, String sql, Object... args) {
        return context.getBean(JdbcTemplate.class).queryForObject(sql, Integer.class, args);
    }
}
