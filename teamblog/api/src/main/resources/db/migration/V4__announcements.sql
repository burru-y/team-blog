-- 公告与更新日志
--
-- 一张表装两种东西：NOTICE（临时通知，如「今晚维护」）和 CHANGELOG（版本更新记录）。
-- 不拆两张表是因为它们的读取方式完全一样（按时间倒序、置顶优先），
-- 拆开只会让「首页显示最新一条」这类查询变复杂。
CREATE TABLE announcements (
    id           UUID PRIMARY KEY,
    kind         VARCHAR(20) NOT NULL,
    -- 版本号：更新日志必填，普通公告可为空
    version      VARCHAR(20),
    title        VARCHAR(200) NOT NULL,
    body_md      TEXT NOT NULL,
    is_pinned    BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 谁发的：留痕，出问题能追溯
    author_id    UUID REFERENCES users (id)
);

-- 首页/公告页都按「置顶 + 时间倒序」取，这条索引直接对应查询
CREATE INDEX idx_announcements_feed ON announcements (is_pinned DESC, published_at DESC);

-- 约束而不是靠应用层校验：版本号格式错在写入时就该失败
ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_kind CHECK (kind IN ('NOTICE', 'CHANGELOG'));

-- 更新日志必须有版本号，否则时间线上会出现「没有版本的更新」
ALTER TABLE announcements
    ADD CONSTRAINT ck_announcements_version CHECK (kind <> 'CHANGELOG' OR version IS NOT NULL);
