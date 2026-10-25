# 部署到服务器

从零开始：买机器 → 装环境 → 起服务 → 配 HTTPS → 日常运维。

本次选择：**香港或境外 VPS + 域名后配**。先用 IP 跑通，域名解析好之后再加证书。

---

## 1. 买服务器

### 配置

| 项 | 最低 | 建议 |
|---|---|---|
| CPU | 1 核 | 2 核 |
| 内存 | 2 GB | 2 GB（这套服务常驻约 700 MB，2 GB 够） |
| 硬盘 | 20 GB | 40 GB SSD |
| 系统 | Ubuntu 22.04 | **Ubuntu 24.04 LTS** |
| 带宽 | 1 Mbps | 3 Mbps 以上 |

### 为什么推荐香港/境外

- **不需要 ICP 备案**，买完就能用
- 内地服务器必须先备案（约 1–2 周，期间域名不能对外提供 Web 服务）
- 代价：内地访问延迟大约 40–120 ms，比内地机房慢，但静态资源已由 nginx 压缩

### 可选商家

| 商家 | 参考价 | 说明 |
|---|---|---|
| 阿里云香港轻量 | ¥24/月（2C2G） | 界面中文，支付宝付款 |
| 腾讯云香港轻量 | ¥30/月（2C2G） | 同上 |
| Vultr | $6/月（1C1G） | 按小时计费，可随时销毁重建 |
| BandwagonHost | $50/年 | 便宜，但客服与稳定性一般 |

买的时候**只开 SSH 密钥登录**，不要用密码登录。

---

## 2. 初始化服务器

以 Ubuntu 24.04 + root 为例。第一次登录：

```bash
ssh root@<服务器IP>
```

### 2.1 建普通用户（不建议长期用 root）

```bash
adduser deploy
usermod -aG sudo deploy
rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy
```

之后一律用 `ssh deploy@<IP>`。

### 2.2 装 Docker

```bash
# 官方脚本，装完后 docker 命令需要 sudo
curl -fsSL https://get.docker.com | sh

# 让 deploy 用户免 sudo 使用 docker
sudo usermod -aG docker deploy
# 退出重新登录，权限才生效
```

验证：

```bash
docker version
docker compose version
```

### 2.3 防火墙

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status
```

**只开这三个端口**。数据库、API、AI 都在 Docker 内部网络，不需要对外。

### 2.4 时间同步（重要）

打卡按天计算，时区与时钟偏移会直接导致“昨天打了卡今天显示没打”：

```bash
sudo timedatectl set-timezone Asia/Shanghai
timedatectl
```

---

## 3. 部署代码

### 3.1 拉代码

```bash
sudo apt install -y git
git clone https://github.com/burru-y/team-blog.git
cd team-blog/teamblog/ops/compose
```

### 3.2 配置环境变量

```bash
cp .env.prod.example .env.prod
chmod 600 .env.prod
```

生成三个密钥（**不要手打**）：

```bash
openssl rand -base64 24   # → POSTGRES_PASSWORD
openssl rand -base64 48   # → JWT_SECRET
openssl rand -base64 32   # → INTERNAL_HMAC_SECRET
```

编辑 `.env.prod`：

```bash
nano .env.prod
```

关键项：

| 变量 | 先 IP 访问时 | 之后换域名 + HTTPS |
|---|---|---|
| `POSTGRES_PASSWORD` | 随机串 | 不变 |
| `JWT_SECRET` | 随机串 | 不变（改了所有人要重新登录） |
| `INTERNAL_HMAC_SECRET` | 随机串 | 不变 |
| `ADMIN_BOOTSTRAP_PASSWORD` | 设一个强密码 | 建号后可以删掉 |
| `COOKIE_SECURE` | **false** | **true** |
| `AI_API_KEY` | 你的 DeepSeek key | 不变 |

`COOKIE_SECURE=true` 却在 HTTP 下访问，浏览器会拒绝保存登录 Cookie——表现是「登录成功但刷新后又变访客」。这是最容易踩的一个坑。

### 3.3 校验配置

```bash
docker compose -f compose.prod.yaml --env-file .env.prod config > /dev/null && echo 配置正确
```

有必填项没填会直接报错并指出变量名。

### 3.4 启动

```bash
docker compose -f compose.prod.yaml --env-file .env.prod up -d --build
```

首次构建约 5–10 分钟（要下载依赖）。构建完成后看状态：

```bash
docker compose -f compose.prod.yaml --env-file .env.prod ps
```

六个服务都应是 `Up`；`db`、`api`、`ai`、`nginx` 应显示 `(healthy)`。

---

## 4. 验证

```bash
# 首页
curl -I http://<服务器IP>/

# API 健康检查
curl http://<服务器IP>/api/v1/health

# 用浏览器打开，注册第一个管理员（见 5.1）
```

如果 403/502：

```bash
docker compose -f compose.prod.yaml --env-file .env.prod logs --tail=50 nginx
docker compose -f compose.prod.yaml --env-file .env.prod logs --tail=50 api
```

---

## 5. 首次使用

### 5.1 建管理员

`.env.prod` 里设了 `ADMIN_BOOTSTRAP_PASSWORD`，且数据库为空，启动时会自动创建：

```
用户名：ADMIN_BOOTSTRAP_USERNAME（默认 admin）
密码：  ADMIN_BOOTSTRAP_PASSWORD
```

**首次登录后立刻改密码**，然后从 `.env.prod` 删掉 `ADMIN_BOOTSTRAP_PASSWORD` 并重启 api：

```bash
docker compose -f compose.prod.yaml --env-file .env.prod up -d api
```

> 留着的风险：只要数据库被清空（比如误删卷），下次启动会用这个密码重建管理员。

### 5.2 邀请成员

后台 → 邀请链接 → 生成 → 复制链接发给同学。链接默认 7 天有效，只能使用一次。

---

## 6. 换域名 + HTTPS

有了域名之后。

### 6.1 解析

在域名服务商添加 A 记录，指向服务器 IP：

```
yuxiao.example.com   A   <服务器IP>
```

内地服务器还需要先完成 ICP 备案，否则解析不生效。

### 6.2 申请证书（Let's Encrypt）

Let's Encrypt **不能给 IP 签发证书**，必须用域名。用 certbot 的 DNS 或 HTTP 验证：

```bash
sudo apt install -y certbot
sudo certbot certonly --standalone -d yuxiao.example.com
```

> 运行前先停掉占用 80 端口的 nginx：`docker compose ... stop nginx`

证书会生成在：

```
/etc/letsencrypt/live/yuxiao.example.com/fullchain.pem
/etc/letsencrypt/live/yuxiao.example.com/privkey.pem
```

拷到项目目录，并让 nginx 容器能读到：

```bash
cd ~/team-blog/teamblog/ops/nginx
sudo cp /etc/letsencrypt/live/yuxiao.example.com/fullchain.pem certs/
sudo cp /etc/letsencrypt/live/yuxiao.example.com/privkey.pem certs/
sudo chown -R $USER:$USER certs
```

### 6.3 切到 HTTPS

```bash
cd ~/team-blog/teamblog/ops/compose
nano .env.prod            # COOKIE_SECURE=true
docker compose -f compose.prod.yaml -f compose.prod.tls.yaml --env-file .env.prod up -d
```

打开 `https://yuxiao.example.com` 验证。

### 6.4 自动续期

证书 90 天过期。加一条定时任务：

```bash
sudo crontab -e
```

```cron
0 4 1 * * certbot renew --quiet --pre-hook "cd /home/deploy/team-blog/teamblog/ops/compose && docker compose -f compose.prod.yaml -f compose.prod.tls.yaml stop nginx" --post-hook "cp /etc/letsencrypt/live/yuxiao.example.com/*.pem /home/deploy/team-blog/teamblog/ops/nginx/certs/ && cd /home/deploy/team-blog/teamblog/ops/compose && docker compose -f compose.prod.yaml -f compose.prod.tls.yaml start nginx"
```

---

## 7. 日常运维

### 查看状态与日志

```bash
cd ~/team-blog/teamblog/ops/compose
alias dc='docker compose -f compose.prod.yaml --env-file .env.prod'

dc ps
dc logs -f --tail=100 api
```

### 更新代码

```bash
cd ~/team-blog
git pull
cd teamblog/ops/compose
dc up -d --build
```

只改了前端就只重建 web：`dc up -d --build web`。

### 备份

备份容器每天 `BACKUP_AT`（默认 03:10）自动执行，保留 14 天。手动备份：

```bash
dc exec backup /bin/sh -c 'pg_dump -Fc -f /backup/manual-$(date +%F).dump'
```

把备份拉到本地（**定期做**，服务器挂了备份也一起没了）：

```bash
# 在你自己电脑上执行
scp deploy@<服务器IP>:/var/lib/docker/volumes/yuxiao_backups/_data/latest.dump ./
```

> 卷名前缀默认是 compose 的 `name`（本文件是 `yuxiao`）。用 `docker volume ls` 确认。

### 恢复

```bash
dc stop api web
dc exec -T db pg_restore -U blog -d blog --clean --if-exists < backup.dump
dc start api web
```

### 磁盘清理

Docker 构建缓存会越积越多：

```bash
docker system df
docker image prune -f
docker builder prune -f --filter until=168h
```

---

## 8. 常见问题

**登录成功但刷新后变回访客**
`COOKIE_SECURE=true` 而站点是 HTTP。改成 `false` 后重启 api。

**502 Bad Gateway**
api 或 web 容器没起来。`dc ps` 看状态，`dc logs api` 看原因。

**AI 一直「服务暂时不可用」**
`.env.prod` 里 `AI_PROVIDER` 还是 `mock`，或 `AI_API_KEY` 没填。`dc logs ai` 会显示上游报错。

**打卡日期差一天**
服务器时区不是 Asia/Shanghai，或时钟没同步。`timedatectl` 检查。

**内存占用高**
`docker stats` 看是哪个容器。api 已限制 384 MB 堆、640 MB 上限；web 是 Node，512 MB 上限。如果长期贴着上限，需要升配置。

---

## 9. 之后可以再优化的

- 把 nginx 换成 Caddy，证书自动申请与续期，省掉第 6.4 节的手工 cron
- 加 uptime 监控（UptimeRobot 免费版够用），站点挂了能收到邮件
- 备份同步到对象存储（阿里云 OSS / Cloudflare R2），避免服务器整机故障丢数据
