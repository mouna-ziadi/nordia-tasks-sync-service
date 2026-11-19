package com.nordia.tasks.api;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared helpers of the tests that call the API. */
abstract class ApiTestSupport {

    static final String KEY = "Idempotency-Key";

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    static String uuid() {
        return UUID.randomUUID().toString();
    }

    ResultActions createTask(String key, String json) throws Exception {
        return mvc.perform(post("/tasks").header(KEY, key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions patchTask(String key, String id, String json) throws Exception {
        return mvc.perform(patch("/tasks/{id}", id).header(KEY, key)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    ResultActions deleteTask(String key, String id, int baseVersion) throws Exception {
        return mvc.perform(delete("/tasks/{id}", id).header(KEY, key)
                .param("baseVersion", String.valueOf(baseVersion)));
    }

    String newTask(String title) throws Exception {
        String id = uuid();
        createTask(uuid(), "{\"id\":\"" + id + "\",\"title\":\"" + title + "\"}")
                .andExpect(status().isCreated());
        return id;
    }
}