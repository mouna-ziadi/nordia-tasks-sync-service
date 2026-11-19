package com.nordia.tasks.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
        "nordia.test.context=concurrency",
        "spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"})
class ConcurrencyTest extends ApiTestSupport {

    private static final int WRITERS = 8;

    @Autowired JdbcTemplate jdbc;

    private record Answer(int status, String body, String replay) {}

    @Test
    void onlyOneOfSeveralSimultaneousPatchesWithTheSameBaseVersionWins() throws Exception {
        String id = newTask("Start");

        List<Integer> statuses = runTogether(() ->
                patchTask(uuid(), id, "{\"baseVersion\":1,\"title\":\"Writer " + uuid() + "\"}")
                        .andReturn().getResponse().getStatus());

        assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(WRITERS - 1);
        mvc.perform(get("/tasks/" + id)).andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void sameKeySentSeveralTimesAtOnceIsAppliedOnlyOnce() throws Exception {
        String id = uuid();
        String key = uuid();
        String json = "{\"id\":\"" + id + "\",\"title\":\"Created once\"}";

        List<Answer> answers = runTogether(() -> {
            var response = createTask(key, json).andReturn().getResponse();
            return new Answer(response.getStatus(), response.getContentAsString(),
                    response.getHeader("Idempotent-Replay"));
        });

        assertThat(answers).allMatch(a -> a.status() == 201);
        assertThat(answers.stream().map(Answer::body).distinct().count()).isEqualTo(1);
        assertThat(answers.stream().filter(a -> a.replay() == null).count()).isEqualTo(1);
        assertThat(answers.stream().filter(a -> "true".equals(a.replay())).count()).isEqualTo(WRITERS - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT version FROM task WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    /** Runs the same job in {@code WRITERS} threads released at the same moment. */
    private <T> List<T> runTogether(Callable<T> job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        try {
            CountDownLatch ready = new CountDownLatch(WRITERS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < WRITERS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return job.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
