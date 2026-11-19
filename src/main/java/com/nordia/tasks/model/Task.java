package com.nordia.tasks.model;

/** A task as exposed by the API. Dates are kept as strings on purpose (see Timestamps). */
public record Task(
        String id,
        String title,
        String note,
        String status,
        String assigneeId,
        String dueDate,
        int version,
        boolean deleted,
        String updatedAt) {}