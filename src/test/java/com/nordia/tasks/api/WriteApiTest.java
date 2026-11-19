package com.nordia.tasks.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "nordia.test.context=writes")
class WriteApiTest {

    private static final String KEY = "Idempotency-Key";

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ---- helpers

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private ResultActions createTask(String key, String json) throws Exception {
        return mvc.perform(post("/tasks").header(KEY, key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchTask(String key, String id, String json) throws Exception {
        return mvc.perform(patch("/tasks/{id}", id).header(KEY, key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deleteTask(String key, String id, int baseVersion) throws Exception {
        return mvc.perform(delete("/tasks/{id}", id).header(KEY, key)
                .param("baseVersion", String.valueOf(baseVersion)));
    }

    private String newTask(String title) throws Exception {
        String id = uuid();
        createTask(uuid(), "{\"id\":\"" + id + "\",\"title\":\"" + title + "\"}")
                .andExpect(status().isCreated());
        return id;
    }

    // ---- tests

    @Test
    void createReturnsVersionOneWithDefaults() throws Exception {
        String id = uuid();
        createTask(uuid(), "{\"id\":\"" + id + "\",\"title\":\"  Check the boiler  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Check the boiler"))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.note").isEmpty())
                .andExpect(jsonPath("$.assigneeId").isEmpty())
                .andExpect(jsonPath("$.dueDate").isEmpty())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.deleted").value(false));
        mvc.perform(get("/tasks/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Check the boiler"));
    }

    @Test
    void patchBumpsVersionAndStaleBaseVersionIsRefusedWithCurrentTask() throws Exception {
        String id = newTask("Old");
        patchTask(uuid(), id, "{\"baseVersion\":1,\"title\":\"New\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.title").value("New"));
        patchTask(uuid(), id, "{\"baseVersion\":1,\"title\":\"Other\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version_conflict"))
                .andExpect(jsonPath("$.current.version").value(2))
                .andExpect(jsonPath("$.current.title").value("New"));
    }

    @Test
    void nullClearsNoteAssigneeAndDueDate() throws Exception {
        String id = uuid();
        createTask(uuid(), "{\"id\":\"" + id + "\",\"title\":\"T\",\"note\":\"n\","
                + "\"assigneeId\":\"m-01\",\"dueDate\":\"2026-12-01\"}")
                .andExpect(status().isCreated());
        patchTask(uuid(), id, "{\"baseVersion\":1,\"note\":null,\"assigneeId\":null,\"dueDate\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").isEmpty())
                .andExpect(jsonPath("$.assigneeId").isEmpty())
                .andExpect(jsonPath("$.dueDate").isEmpty())
                .andExpect(jsonPath("$.title").value("T"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void patchWithoutRealChangeDoesNotBumpTheVersion() throws Exception {
        String id = newTask("Same");
        patchTask(uuid(), id, "{\"baseVersion\":1,\"title\":\"Same\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void doneTaskCannotGoBackToTodoButCanGoToDoing() throws Exception {
        String id = uuid();
        createTask(uuid(), "{\"id\":\"" + id + "\",\"title\":\"T\",\"status\":\"done\"}")
                .andExpect(status().isCreated());
        patchTask(uuid(), id, "{\"baseVersion\":1,\"status\":\"todo\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("invalid_transition"));
        patchTask(uuid(), id, "{\"baseVersion\":1,\"status\":\"doing\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("doing"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void deletedTaskIsATombstone() throws Exception {
        String id = newTask("To delete");
        deleteTask(uuid(), id, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.version").value(2));
        // Changing a deleted task: 410, with the task as it is now.
        patchTask(uuid(), id, "{\"baseVersion\":1,\"title\":\"Too late\"}")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("task_deleted"))
                .andExpect(jsonPath("$.current.deleted").value(true));
        // Deleting again, even with an old version: 200, nothing changes.
        deleteTask(uuid(), id, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"title\":\"\"",
            "\"title\":5",
            "\"title\":\"x\",\"status\":\"finished\"",
            "\"title\":\"x\",\"assigneeId\":\"m-99\"",
            "\"title\":\"x\",\"dueDate\":\"2026-02-30\"",
            "\"title\":\"x\",\"priority\":1"})
    void invalidCreateIsRefused(String fragment) throws Exception {
        createTask(uuid(), "{\"id\":\"" + uuid() + "\"," + fragment + "}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void titleOverOneHundredTwentyCharactersIsRefused() throws Exception {
        createTask(uuid(), "{\"id\":\"" + uuid() + "\",\"title\":\"" + "x".repeat(121) + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void missingKeyAndBadJsonAreRefusedWithoutTouchingData() throws Exception {
        mvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_idempotency_key"));
        createTask("abc", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("missing_idempotency_key"));
        createTask(uuid(), "{")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_json"));
        createTask(uuid(), "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_json"));
    }

    @Test
    void unknownTaskIsNotFoundForPatchAndDelete() throws Exception {
        patchTask(uuid(), uuid(), "{\"baseVersion\":1,\"title\":\"x\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
        deleteTask(uuid(), uuid(), 1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("not_found"));
    }

    @Test
    void creatingAnExistingIdWithANewKeyIsAConflict() throws Exception {
        String id = newTask("First");
        createTask(uuid(), "{\"id\":\"" + id + "\",\"title\":\"Second\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("id_exists"))
                .andExpect(jsonPath("$.current.title").value("First"));
    }
}