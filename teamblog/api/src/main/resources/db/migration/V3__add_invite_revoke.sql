-- 邀请撤销。
--
-- 为什么用单独的 revoked_at 而不是把 expires_at 改成过去：
-- 过期与撤销是两件事，排障时需要区分"没人用，自然过期了"和"管理员主动作废了"。
-- 合成一个字段会让审计信息丢失。
ALTER TABLE admin_invites
    ADD COLUMN revoked_at timestamptz;

COMMENT ON COLUMN admin_invites.revoked_at IS '撤销时间；非空表示该邀请被管理员作废';

CREATE INDEX ix_invites_created ON admin_invites (created_at DESC);
