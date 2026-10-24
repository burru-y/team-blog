-- 显示名：用户可自定义的昵称，与登录名分开。
--
-- 为什么不在原表上把 username 改成可中文：
-- username 同时是登录名和 /u/{username} 的路由段，允许中文会带来 URL 编码、
-- 大小写归一、唯一性比对等一系列问题。显示与登录分开是更便宜的方案。
--
-- display_name 可空：为空时接口回退到 username，不需要给历史数据补值。
ALTER TABLE users
    ADD COLUMN display_name varchar(30);

COMMENT ON COLUMN users.display_name IS '显示名（昵称），为空时前端回退显示 username';
