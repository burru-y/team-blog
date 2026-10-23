-- 迁移之后执行一次（开发与生产都要）。幂等，可重复执行。
-- 只授予 AI 服务确实需要的权限，不多给。
GRANT SELECT ON v_projects_public TO ai_reader;
GRANT SELECT, INSERT, UPDATE ON ai_conversations TO ai_reader;
GRANT SELECT, INSERT, UPDATE ON ai_usage_daily TO ai_reader;
