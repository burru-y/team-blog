package com.teamblog.todo;

import com.teamblog.common.UuidUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * 个人待办。
 *
 * <p>刻意做得很薄：一条待办只有"文字 + 是否完成"。加上截止时间、优先级、子任务会变成
 * 另一个产品；主页上需要的只是"我最近要做什么"。
 */
@Entity
@Table(name = "todos")
public class Todo {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false)
    private boolean done;

    /** 录入顺序：新条目排在同组末尾，勾掉后位置不跳。 */
    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    protected Todo() {
    }

    public static Todo create(UUID userId, String label, int position, OffsetDateTime at) {
        Todo todo = new Todo();
        todo.id = UuidUtils.v7();
        todo.userId = Objects.requireNonNull(userId, "userId 不能为空");
        todo.label = Objects.requireNonNull(label, "label 不能为空");
        todo.done = false;
        todo.position = position;
        todo.createdAt = Objects.requireNonNull(at, "createdAt 不能为空");
        return todo;
    }

    public void toggle(OffsetDateTime at) {
        this.done = !this.done;
        this.doneAt = this.done ? at : null;
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDone() {
        return done;
    }

    public int getPosition() {
        return position;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDoneAt() {
        return doneAt;
    }
}
