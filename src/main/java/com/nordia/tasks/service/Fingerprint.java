package com.nordia.tasks.service;

import com.nordia.tasks.util.Json;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Identifies "the same request": method, URL and body (key order ignored). */
final class Fingerprint {

    private Fingerprint() {}

    static String of(String method, String target, Object body) {
        String text = method + " " + target + " " + Json.canonical(body);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
