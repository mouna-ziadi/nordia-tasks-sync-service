package com.nordia.tasks.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "nordia.test.context=idempotency")
class IdempotencyTest extends ApiTestSupport {

    private static final String REPLAY = "Idempotent-Replay";

    @Test
    void replayedPatchReturnsTheOriginalAnswerAndIsNotAppliedTwice() throws Exception {
        String id = newTask("Start");
        String key = uuid();
        String body = "{\"baseVersion\":1,\"title\":\"Once\"}";

        String first = patchTask(key, id, body)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(REPLAY))
                .andExpect(jsonPath("$.version").value(2))
                .andReturn().getResponse().getContentAsString();

        // The client lost the answer and sends the very same operation again.
        String replay = patchTask(key, id, body)
                .andExpect(status().isOk())
                .andExpect(header().string(REPLAY, "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
        mvc.perform(get("/tasks/" + id)).andExpect(jsonPath("$.version").value(2)); // not 3

        // Without the key, the same operation is a new one, and it is now outdated.
        patchTask(uuid(), id, body).andExpect(status().isConflict());
    }

    @Test
    void replayedCreateReturnsTheOriginal201NotIdExists() throws Exception {
        String id = uuid();
        String key = uuid();
        String json = "{\"id\":\"" + id + "\",\"title\":\"Once\"}";

        String first = createTask(key, json)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String replay = createTask(key, json)
                .andExpect(status().isCreated())
                .andExpect(header().string(REPLAY, "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(replay).isEqualTo(first);
    }

    @Test
    void replayAfterLaterChangesStillReturnsTheOriginalAnswer() throws Exception {
        String id = uuid();
        String key = uuid();
        String json = "{\"id\":\"" + id + "\",\"title\":\"Original\"}";
        String first = createTask(key, json).andReturn().getResponse().getContentAsString();

        patchTask(uuid(), id, "{\"baseVersion\":1,\"title\":\"Changed\"}").andExpect(status().isOk());

        // The answer to a key is final: it is the answer of that moment, not the task as it is now.
        String replay = createTask(key, json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("Original"))
                .andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);
    }

    @Test
    void sameKeyForADifferentRequestIsRefused() throws Exception {
        String id = newTask("Start");
        String key = uuid();
        patchTask(key, id, "{\"baseVersion\":1,\"title\":\"A\"}").andExpect(status().isOk());

        patchTask(key, id, "{\"baseVersion\":1,\"title\":\"B\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency_key_reuse"));
        deleteTask(key, id, 2)   // another method and URL with the same key
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency_key_reuse"));

        mvc.perform(get("/tasks/" + id))
                .andExpect(jsonPath("$.title").value("A"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.deleted").value(false));
    }

    @Test
    void errorAnswersAreReplayedToo() throws Exception {
        String id = newTask("Start");
        patchTask(uuid(), id, "{\"baseVersion\":1,\"title\":\"Other\"}").andExpect(status().isOk());

        String key = uuid();
        String stale = "{\"baseVersion\":1,\"title\":\"Late\"}";
        String first = patchTask(key, id, stale)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version_conflict"))
                .andReturn().getResponse().getContentAsString();
        String replay = patchTask(key, id, stale)
                .andExpect(status().isConflict())
                .andExpect(header().string(REPLAY, "true"))
                .andReturn().getResponse().getContentAsString();
        assertThat(replay).isEqualTo(first);

        // After a 409, a new attempt is a new operation: new key, and the right baseVersion.
        patchTask(uuid(), id, "{\"baseVersion\":2,\"title\":\"Late\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void theOrderOfJsonKeysDoesNotMakeAnotherRequest() throws Exception {
        String id = newTask("Start");
        String key = uuid();
        patchTask(key, id, "{\"baseVersion\":1,\"title\":\"Same\"}").andExpect(status().isOk());
        patchTask(key, id, "{\"title\":\"Same\",\"baseVersion\":1}")
                .andExpect(status().isOk())
                .andExpect(header().string(REPLAY, "true"));
    }
}
