-- 个人名片强调色
--
-- 存十六进制（#RRGGBB）。不做服务端校验"是否好看"，只校验格式：
-- 色板由前端给，用户也可以自己填。
ALTER TABLE users ADD COLUMN accent_color VARCHAR(9);
