-- ============ 扩展 ============
-- pgcrypto 只用于种子里生成 bcrypt 哈希；PG13+ 的 gen_random_uuid 是内置的
CREATE
EXTENSION IF NOT EXISTS pgcrypto;

-- ============ 用户 ============
CREATE TABLE users
(
    id uuid PRIMARY KEY,
    username      varchar(20)  NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(10)  NOT NULL DEFAULT 'MEMBER',
    avatar_url    varchar(500),
    bio           text,
    github_url    varchar(300),
    email_public  boolean      NOT NULL DEFAULT false,
    total_points  integer      NOT NULL DEFAULT 0,
    token_version integer      NOT NULL DEFAULT 0,
    last_login_at timestamptz,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT ck_users_username CHECK (username ~ '^[a-z0-9_]{3,20}$'
) ,
    CONSTRAINT ck_users_points   CHECK (total_points >= 0)
);

-- 邮箱大小写不敏感（注册时也统一转小写，双保险）
CREATE UNIQUE INDEX uq_users_email_lower ON users (lower(email) );

CREATE TABLE refresh_tokens
(
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    replaced_by_id uuid,
    ip_hash    varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_user_exp ON refresh_tokens (user_id, expires_at);
CREATE INDEX ix_refresh_expires ON refresh_tokens (expires_at);

-- ============ 成果 ============
CREATE TABLE projects
(
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES users (id),
    type        varchar(20)  NOT NULL,
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    title       varchar(200) NOT NULL,
    description text         NOT NULL,
    github_url  varchar(300),
    content_md  text,
    image_url   varchar(500),
    removed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_projects_type CHECK (type IN ('PROJECT', 'CERTIFICATE', 'AWARD')),
    CONSTRAINT ck_projects_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    -- v1.0 必填字段在数据库层兜底：项目必须有链接+内容，证书/奖项必须有图或链接
    CONSTRAINT ck_projects_required CHECK (
        (type = 'PROJECT' AND github_url IS NOT NULL AND content_md IS NOT NULL)
            OR (type IN ('CERTIFICATE', 'AWARD') AND (image_url IS NOT NULL OR github_url IS NOT NULL))
        ),
    CONSTRAINT ck_projects_title CHECK (char_length(btrim(title)) > 0)
);
CREATE INDEX ix_projects_feed ON projects (status, created_at DESC);
CREATE INDEX ix_projects_owner ON projects (owner_id, status);
CREATE INDEX ix_projects_type ON projects (type, status);

-- ============ 学习内容 ============
CREATE TABLE learning_logs
(
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    title      varchar(200) NOT NULL,
    content_md text         NOT NULL,
    summary_md text,
    type       varchar(10)  NOT NULL DEFAULT 'NOTE',
    log_date   date         NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_logs_type CHECK (type IN ('NOTE', 'CODE')),
    CONSTRAINT ck_logs_title CHECK (char_length(btrim(title)) > 0)
);
CREATE INDEX ix_logs_user_created ON learning_logs (user_id, created_at DESC);
CREATE INDEX ix_logs_created ON learning_logs (created_at DESC);
CREATE INDEX ix_logs_log_date ON learning_logs (log_date);

-- ============ 打卡与积分 ============
CREATE TABLE checkins
(
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    checkin_date date NOT NULL,
    note         varchar(200),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_checkins_user_date UNIQUE (user_id, checkin_date)
);
CREATE INDEX ix_checkins_date ON checkins (checkin_date);

CREATE TABLE point_events
(
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    kind   varchar(20) NOT NULL,
    ref_id varchar(64) NOT NULL,
    points integer     NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_points_kind CHECK (kind IN ('CHECKIN', 'LEARNING_LOG', 'PROJECT',
                                              'STREAK_7', 'STREAK_14', 'STREAK_30')),
    CONSTRAINT ck_points_value CHECK (points > 0),
    -- “只加一次”的最终保证：同一事件唯一
    CONSTRAINT uq_points_once UNIQUE (user_id, kind, ref_id)
);
CREATE INDEX ix_points_user_created ON point_events (user_id, created_at DESC);

-- ============ 评论（两级） ============
CREATE TABLE comments
(
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users (id),
    target_type varchar(20) NOT NULL,
    target_id uuid NOT NULL,
    parent_id uuid REFERENCES comments (id),
    content     text        NOT NULL,
    deleted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_comments_target CHECK (target_type IN ('PROJECT', 'LEARNING_LOG')),
    CONSTRAINT ck_comments_len CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_comments_not_self CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX ix_comments_target ON comments (target_type, target_id, created_at);
CREATE INDEX ix_comments_parent ON comments (parent_id);

-- 数据库层禁止“回复的回复”，比只靠 service 判断可靠
CREATE
OR
REPLACE FUNCTION check_comment_depth() RETURNS trigger AS $$
BEGIN IF NEW.parent_id IS NOT NULL THEN
        IF EXISTS (SELECT 1 FROM comments WHERE id = NEW.parent_id AND parent_id IS NOT NULL) THEN
            RAISE EXCEPTION 'REPLY_DEPTH_EXCEEDED';
END IF;
END IF;
RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_comment_depth
    BEFORE INSERT
    ON comments
    FOR EACH ROW EXECUTE FUNCTION check_comment_depth();

-- ============ 留言反馈 ============
CREATE TABLE feedbacks
(
    id uuid PRIMARY KEY,
    name    varchar(50) NOT NULL,
    email   varchar(255),
    content text        NOT NULL,
    status  varchar(10) NOT NULL DEFAULT 'NEW',
    ip_hash varchar(64),
    handled_by uuid REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_feedback_status CHECK (status IN ('NEW', 'READ', 'ARCHIVED')),
    CONSTRAINT ck_feedback_len CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000)
);
CREATE INDEX ix_feedback_status ON feedbacks (status, created_at DESC);

-- ============ 邀请 ============
CREATE TABLE admin_invites
(
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL,
    email      varchar(255),
    role       varchar(10) NOT NULL DEFAULT 'MEMBER',
    created_by uuid NOT NULL REFERENCES users (id),
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_invite_hash UNIQUE (token_hash),
    CONSTRAINT ck_invite_role CHECK (role IN ('ADMIN', 'MEMBER'))
);
CREATE INDEX ix_invites_exp ON admin_invites (expires_at);

-- ============ AI ============
CREATE TABLE ai_conversations
(
    id uuid PRIMARY KEY,
    visitor_ip_hash varchar(64),
    project_id uuid REFERENCES projects (id),
    messages jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_conv_project ON ai_conversations (project_id, created_at DESC);
CREATE INDEX ix_ai_conv_created ON ai_conversations (created_at);

-- 全局/单 IP 日调用计数：scope = 'GLOBAL' 或 IP 哈希
CREATE TABLE ai_usage_daily
(
    day   date        NOT NULL,
    scope varchar(64) NOT NULL,
    calls integer     NOT NULL DEFAULT 0,
    PRIMARY KEY (day, scope)
);

-- ============ 给 AI 服务的只读视图 ============
CREATE VIEW v_projects_public AS
SELECT id, type, title, description, content_md, github_url
FROM projects
WHERE status = 'ACTIVE';

-- ============ updated_at 兜底 ============
CREATE
OR
REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN NEW.updated_at = now();
RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_projects_updated
    BEFORE UPDATE
    ON projects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
