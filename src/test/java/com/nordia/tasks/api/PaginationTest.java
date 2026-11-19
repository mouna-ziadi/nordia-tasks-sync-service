package com.nordia.tasks.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "nordia.test.context=pagination")
class PaginationTest extends ApiTestSupport {

    @Autowired JdbcTemplate jdbc;

    private record Page(List<String> ids, List<Boolean> deleted, String next, boolean hasMore) {}

    @Test
    void deletionsBetweenPagesNeverSkipOrDuplicateAndTombstonesAreDelivered() throws Exception {
        // In the seed, changes 5 and 30 are alive tasks. Page 1 holds changes 1 to 20.
        String readAlready = idAtSeq(5);
        String notYetRead = idAtSeq(30);

        Page first = read(null);
        assertThat(first.ids()).hasSize(20);
        assertThat(first.next()).isEqualTo("c20");
        assertThat(first.ids()).contains(readAlready).doesNotContain(notYetRead);

        // Two colleagues delete tasks while we are between two pages.
        deleteTask(uuid(), readAlready, versionOf(readAlready)).andExpect(status().isOk());
        deleteTask(uuid(), notYetRead, versionOf(notYetRead)).andExpect(status().isOk());

        // We carry on from the cursor we were given.
        List<String> later = new ArrayList<>();
        Map<String, Boolean> deletedInLastVersion = new HashMap<>();
        String cursor = first.next();
        boolean hasMore = true;
        while (hasMore) {
            Page page = read(cursor);
            for (int i = 0; i < page.ids().size(); i++) {
                later.add(page.ids().get(i));
                deletedInLastVersion.put(page.ids().get(i), page.deleted().get(i));
            }
            cursor = page.next();
            hasMore = page.hasMore();
        }

        // 25 changes were after the cursor. Task 30 moved to the end of the line, and task 5
        // came back after being read: 25 - 1 + 2 = 26 deliveries, each task only once.
        assertThat(later).hasSize(26).doesNotHaveDuplicates();

        // Nothing was skipped: together with page 1, all 45 tasks were delivered.
        Set<String> seen = new HashSet<>(first.ids());
        seen.addAll(later);
        assertThat(seen).hasSize(45);

        // Both deletions reach us as tombstones, including the task we had already read.
        assertThat(deletedInLastVersion.get(readAlready)).isTrue();
        assertThat(deletedInLastVersion.get(notYetRead)).isTrue();

        // Up to date: nothing more to read, and the cursor does not move.
        Page upToDate = read(cursor);
        assertThat(upToDate.ids()).isEmpty();
        assertThat(upToDate.next()).isEqualTo(cursor);
    }

    private Page read(String since) throws Exception {
        var request = get("/tasks").param("limit", "20");
        if (since != null) {
            request = request.param("since", since);
        }
        String body = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        List<Boolean> deleted = JsonPath.read(body, "$.items[*].deleted");
        String next = JsonPath.read(body, "$.nextCursor");
        boolean hasMore = JsonPath.read(body, "$.hasMore");
        return new Page(ids, deleted, next, hasMore);
    }

    private String idAtSeq(long seq) {
        return jdbc.queryForObject("SELECT id FROM task WHERE seq = ?", String.class, seq);
    }

    private int versionOf(String id) {
        return jdbc.queryForObject("SELECT version FROM task WHERE id = ?", Integer.class, id);
    }
}
