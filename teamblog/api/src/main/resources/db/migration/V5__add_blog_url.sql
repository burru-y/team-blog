-- 个人主页增加「个人博客」链接
--
-- 可为空：不是每个人都有独立博客，填了才在个人主页显示。
-- 长度与 github_url 一致（300），都是同类的外链字段。
ALTER TABLE users ADD COLUMN blog_url VARCHAR(300);
