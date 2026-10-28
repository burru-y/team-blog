-- 个人待办
--
-- 每个人在自己的主页上有一份待办清单：写下来、勾掉、清掉已完成的。
-- 归属用 user_id 而不是 username：改名不该丢掉清单。
CREATE TABLE todos (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    label      VARCHAR(120) NOT NULL,
    done       BOOLEAN      NOT NULL DEFAULT FALSE,
    position   INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    done_at    TIMESTAMPTZ
);

-- 主页一次取一个人的全部待办：未完成在前，同组按录入顺序
CREATE INDEX idx_todos_owner ON todos (user_id, done, position, created_at);
