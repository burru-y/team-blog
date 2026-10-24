-- 集成测试专用库（与 dev 的 blog 库隔离）。
--
-- 为什么需要独立库：见 api/src/test/resources/application-test.yml 的说明。
-- 用法：
--   psql "postgresql://blog:blog_dev_pw@localhost:5432/blog" -f ops/init/02-test-db.sql
--
-- 注意：本脚本可重复执行；pgcrypto 由 Flyway V1 自己 CREATE EXTENSION 建好。

SELECT 'CREATE DATABASE blog_test OWNER blog'
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'blog_test')\gexec

ALTER DATABASE blog_test SET timezone TO 'Asia/Shanghai';
