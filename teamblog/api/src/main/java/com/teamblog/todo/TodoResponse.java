package com.teamblog.todo;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 待办对外表示。 */
public record TodoResponse(
        UUID id,
        String label,
        boolean done,
        OffsetDateTime createdAt
) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(todo.getId(), todo.getLabel(), todo.isDone(), todo.getCreatedAt());
    }
}
