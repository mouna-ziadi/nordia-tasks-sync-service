package com.nordia.tasks.api;

import com.nordia.tasks.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Every framework error (unknown route, wrong method, bug) uses the contract's {code, error} format. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handle(Exception ex) {
        if (ex instanceof ErrorResponse spring) {
            int status = spring.getStatusCode().value();
            Result result = switch (status) {
                case 404 -> Result.error(404, "not_found", "Not found");
                case 405 -> Result.error(405, "method_not_allowed", "Method not allowed");
                default -> status >= 500
                        ? Result.error(status, "internal_error", "Unexpected error")
                        : Result.error(status, "bad_request", "Bad request");
            };
            return ResponseEntity.status(status)
                    .headers(spring.getHeaders())   // keeps "Allow" on a 405
                    .contentType(Responses.JSON)
                    .body(result.body());
        }
        log.error("Unexpected error", ex);
        return Responses.of(Result.error(500, "internal_error", "Unexpected error"));
    }
}