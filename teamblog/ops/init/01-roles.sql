-- AI 服务专用只读账号：物理上不能改业务表。
-- 该脚本在容器首次初始化时以超级用户执行；已存在数据卷时不会重跑，需手动执行。

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_reader') THEN
    CREATE ROLE ai_reader LOGIN PASSWORD 'ai_dev_pw';
  END IF;
END $$;

GRANT CONNECT ON DATABASE blog TO ai_reader;
GRANT USAGE ON SCHEMA public TO ai_reader;

-- 关键：默认权限要指定 FOR ROLE，因为表是 blog 建的；
-- 不指定时加的是超级用户自己的默认权限，等于没加。
ALTER DEFAULT PRIVILEGES FOR ROLE blog IN SCHEMA public
  GRANT SELECT ON TABLES TO ai_reader;
