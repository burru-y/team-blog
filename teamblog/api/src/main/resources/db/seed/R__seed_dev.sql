-- 密码统一为 ChangeMe123!，用 pgcrypto 生成 $2a$12$ 格式，Spring 的 BCryptPasswordEncoder 可直接校验
INSERT INTO users (id, username, email, password_hash, role, bio, github_url, total_points)
SELECT gen_random_uuid(),
       'admin' || n,
       'admin' || n || '@teamblog.local',
       crypt('ChangeMe123!', gen_salt('bf', 12)),
       'ADMIN',
       '负责 ' || (ARRAY['后端与部署', '前端', 'AI', '测试与运营'])[n],
       'https://github.com/teamblog',
       0
FROM generate_series(1, 4) AS n
ON CONFLICT (username)
DO NOTHING;

INSERT INTO users (id, username, email, password_hash, role, bio, total_points)
SELECT gen_random_uuid(),
       'member' || n,
       'member' || n || '@teamblog.local',
       crypt('ChangeMe123!', gen_salt('bf', 12)),
       'MEMBER',
       '演示成员 ' || n,
       0
FROM generate_series(1, 8) AS n
ON CONFLICT
    (username)
    DO NOTHING;

-- 近 60 天随机打卡（团队热力图需要数据）
INSERT INTO checkins (id, user_id, checkin_date)
SELECT gen_random_uuid(), u.id, d::date
FROM users u
         CROSS JOIN generate_series(current_date - 59, current_date, interval '1 day') AS d
WHERE u.username LIKE 'member%'
  AND random() < 0.55
ON CONFLICT
    (user_id, checkin_date)
    DO NOTHING;

-- 每次打卡补一条积分流水（顺带验证账本结构）
INSERT INTO point_events (id, user_id, kind, ref_id, points)
SELECT gen_random_uuid(), c.user_id, 'CHECKIN', c.id::text, 1
FROM checkins c
ON CONFLICT
    (user_id, kind, ref_id)
    DO NOTHING;

-- 总分重算：验证“总分 = 流水之和”
UPDATE users u
SET total_points = COALESCE((SELECT sum(p.points) FROM point_events p WHERE p.user_id = u.id), 0);

-- 三类成果各一条（验证 CHECK 约束与列表接口）
INSERT INTO projects (id, owner_id, type, title, description, github_url, content_md, image_url)
    SELECT gen_random_uuid(),
           u.id,
           v.type,
           v.title,
           v.description,
           v.github_url,
           v.content_md,
           v.image_url
    FROM (SELECT id FROM users WHERE username = 'member1') u
             CROSS JOIN (VALUES ('PROJECT', '团队博客脚手架', '把登录、打卡、成果串起来的最小实现',
                                 'https://github.com/teamblog/web', '## 技术栈
Next.js + Spring Boot + FastAPI', NULL),
                                ('CERTIFICATE', '蓝桥杯省一', '2026 年省赛', NULL, NULL, '/uploads/demo/cert.png'),
                                ('AWARD', '校园黑客松三等奖', '48 小时开发', NULL, NULL,
                                 '/uploads/demo/award.png')) AS v(type, title, description, github_url, content_md, image_url)
                        ON CONFLICT DO NOTHING;
