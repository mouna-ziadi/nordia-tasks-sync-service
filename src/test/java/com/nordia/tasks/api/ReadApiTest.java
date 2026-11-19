package com.nordia.tasks.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ReadApiTest {

    private static final String FIRST_TASK = "79ce392b-c44e-48ae-b81c-edd1dd75dc7b";
    private static final String SECOND_TASK = "b09a4e7a-7aa9-446f-8fc4-7206b27058ad";
    private static final String DELETED_TASK = "79238d3e-6a69-4fc7-8cef-4a224321ccb8";
    private static final String TASK_WITH_UNKNOWN_ASSIGNEE = "b1cb23dd-ab23-46ea-aaa3-f8ca5ac63b5b";

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void membersAreListedById() throws Exception {
        mvc.perform(get("/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].id").value("m-01"))
                .andExpect(jsonPath("$[0].name").value("Lina B."))
                .andExpect(jsonPath("$[5].id").value("m-06"));
    }

    @Test
    void firstPageUsesDefaultLimitOfTwenty() throws Exception {
        mvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").value("c20"))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.items[0].id").value(FIRST_TASK));
    }

    @Test
    void pagesCoverAllFortyFiveTasksWithoutOverlap() throws Exception {
        mvc.perform(get("/tasks").param("limit", "20"))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").value("c20"))
                .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/tasks").param("since", "c20").param("limit", "20"))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.nextCursor").value("c40"))
                .andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/tasks").param("since", "c40").param("limit", "20"))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.nextCursor").value("c45"))
                .andExpect(jsonPath("$.hasMore").value(false));
        // Nothing new: same cursor comes back, no items.
        mvc.perform(get("/tasks").param("since", "c45"))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").value("c45"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void emptySinceMeansFromTheBeginning() throws Exception {
        mvc.perform(get("/tasks").param("since", "").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(FIRST_TASK))
                .andExpect(jsonPath("$.nextCursor").value("c2"));
    }

    @Test
    void unknownCursorIsRefused() throws Exception {
        mvc.perform(get("/tasks").param("since", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_cursor"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "abc", "", "-1", "1.5"})
    void invalidLimitIsRefused(String limit) throws Exception {
        mvc.perform(get("/tasks").param("limit", limit))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void taskByIdReturnsAllFields() throws Exception {
        mvc.perform(get("/tasks/" + SECOND_TASK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SECOND_TASK))
                .andExpect(jsonPath("$.title").value("Vérifier les compteurs d'eau du bâtiment A"))
                .andExpect(jsonPath("$.note").value("Couper le circuit avant de commencer."))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.assigneeId").isEmpty())   // present, and null
                .andExpect(jsonPath("$.dueDate").value("2026-11-26"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-06T10:17:40.000Z"));
    }

    @Test
    void deletedTaskIsReturnedAsTombstone() throws Exception {
        mvc.perform(get("/tasks/" + DELETED_TASK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-08T09:54:18.000Z"));
    }

    @Test
    void unknownTaskIsNotFound() throws Exception {
        mvc.perform(get("/tasks/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void unknownAssigneeInSeedIsServedAsIs() throws Exception {
        mvc.perform(get("/tasks/" + TASK_WITH_UNKNOWN_ASSIGNEE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value("m-99"));
    }

    @Test
    void responsesAreUtf8() throws Exception {
        String body = mvc.perform(get("/tasks/" + FIRST_TASK))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("Installer le détecteur de fumée de l'escalier C");
    }

    @Test
    void unknownRouteAndWrongMethodUseTheErrorFormat() throws Exception {
        mvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        mvc.perform(post("/members"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("method_not_allowed"));
    }
}
