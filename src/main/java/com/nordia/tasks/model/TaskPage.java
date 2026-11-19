package com.nordia.tasks.model;

import java.util.List;

public record TaskPage(List<Task> items, String nextCursor, boolean hasMore) {}