package com.nordia.tasks.api;

import com.nordia.tasks.model.Result;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

final class Responses {

    /** The charset is explicit so accents are never written in another encoding. */
    static final MediaType JSON = new MediaType("application", "json", StandardCharsets.UTF_8);

    private Responses() {}

    static ResponseEntity<String> of(Result result) {
        return ResponseEntity.status(result.status()).contentType(JSON).body(result.body());
    }
}