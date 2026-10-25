# 域名与 HTTPS

从「只有 IP」到「`https://yuxiao.xxx` 能访问」，一共五步。其中只有第三步慢（备案）。

```
1. 买域名       5 分钟
2. 实名认证     当天
3. ICP 备案     1-2 周（内地服务器必须）
4. 申请证书     10 分钟
5. 切 HTTPS     10 分钟
```

---

## 第 1 步：买域名

阿里云万网，用那 300 元优惠券。

| 后缀 | 首年 | 说明 |
|---|---|---|
| `.com` | ¥69 | 通用，最稳 |
| `.cn` | ¥29 | 便宜，个人可备案，需实名 |
| `.top` | ¥9 | 最便宜，但部分邮箱/平台会拦 |

买完在「域名控制台 → 信息模板」完成**实名认证**，`.cn` 不实名会 5 天后被停解析。

---

## 第 2 步：先别急着解析

**内地服务器的域名，备案通过前解析到这台机器会被拦截**——浏览器显示「未备案」或直接连不上。

所以顺序是：**先备案，再解析**。

备案期间站点继续用 IP 访问，不影响使用。

---

## 第 3 步：ICP 备案（约 1-2 周）

阿里云 APP 里搜「备案」，或网页版 `beian.aliyun.com`。

需要准备：

| 材料 | 说明 |
|---|---|
| 身份证 | 正反面拍照 |
| 本人手持身份证照 | 按提示拍 |
| 服务器 | **包年包月且剩余时长 ≥ 3 个月** |
| 域名 | 已实名、未过期 |
| 网站名称 | 填「欲晓」或「欲晓团队博客」，不要含「中国」「国家」等词 |
| 网站内容 | 选「其他」或「博客」，描述写技术分享 |

> 你买的是 ¥99/年包年包月，满足「≥3 个月」这条。

流程：填资料 → 阿里云初审（1-2 个工作日）→ 管局审核（约 1-2 周）→ 收到备案号短信。

备案号记下来，之后要放到页脚（法规要求）。

---

## 第 4 步：解析

备案通过后，在「云解析 DNS」加两条记录：

| 记录类型 | 主机记录 | 记录值 |
|---|---|---|
| A | `@` | `47.111.71.156` |
| A | `www` | `47.111.71.156` |

等 10 分钟，验证：

```bash
dig +short yuxiao.xxx
# 应输出 47.111.71.156
```

> 解析生效后，`http://yuxiao.xxx` 就能访问了。此时可以先把地址发给同学，用 HTTP。

---

## 第 5 步：申请证书

Let's Encrypt 免费，90 天有效，可自动续期。

certbot 的 `standalone` 模式要独占 80 端口，**先停掉 nginx**：

```bash
cd /root/teamblog/ops/compose
docker compose -f compose.prod.yaml --env-file .env.prod stop nginx

apt install -y certbot
certbot certonly --standalone -d yuxiao.xxx -d www.yuxiao.xxx
```

按提示填邮箱、同意协议。成功后证书在：

```
/etc/letsencrypt/live/yuxiao.xxx/fullchain.pem
/etc/letsencrypt/live/yuxiao.xxx/privkey.pem
```

拷进项目目录（nginx 容器读的是这里）：

```bash
cd /root/teamblog/ops/nginx
mkdir -p certs
cp -L /etc/letsencrypt/live/yuxiao.xxx/fullchain.pem certs/
cp -L /etc/letsencrypt/live/yuxiao.xxx/privkey.pem certs/
chmod 644 certs/*.pem
```

> `cp -L` 必须带：`/etc/letsencrypt/live/` 下是符号链接，直接 `cp` 会拷到断链。

---

## 第 6 步：切到 HTTPS

**① 改配置**

```bash
cd /root/teamblog/ops/compose
nano .env.prod
```

```env
COOKIE_SECURE=true      # ← 关键，去掉会导致登录状态丢失或 Cookie 不安全
```

**② 用 TLS 覆盖文件启动**

```bash
docker compose -f compose.prod.yaml -f compose.prod.tls.yaml \
  --env-file .env.prod up -d
```

**③ 验证**

```bash
curl -I https://yuxiao.xxx
curl -I http://yuxiao.xxx     # 应返回 301 跳到 https
```

浏览器打开 `https://yuxiao.xxx`，地址栏应显示锁图标。

---

## 第 7 步：自动续期

证书 90 天过期。加一条 cron：

```bash
crontab -e
```

```cron
0 4 1 * * certbot renew --quiet --pre-hook "cd /root/teamblog/ops/compose && docker compose -f compose.prod.yaml -f compose.prod.tls.yaml stop nginx" --post-hook "cp -L /etc/letsencrypt/live/yuxiao.xxx/*.pem /root/teamblog/ops/nginx/certs/ && cd /root/teamblog/ops/compose && docker compose -f compose.prod.yaml -f compose.prod.tls.yaml start nginx"
```

测试续期是否可行（不真正续）：

```bash
certbot renew --dry-run
```

---

## 常见问题

**备案被驳回**
最常见原因是「网站名称」或「网站内容」描述不合规。改成中性的技术描述，不要出现商务、新闻、论坛类词汇。

**证书申请报 `Challenge failed`**
80 端口没通。检查：
```bash
docker compose -f compose.prod.yaml --env-file .env.prod ps nginx
curl -I http://yuxiao.xxx/
```
备案没通过的话 80 会被拦，也会导致这个失败。

**HTTPS 开了但打不开**
看 nginx 日志：
```bash
docker compose -f compose.prod.yaml -f compose.prod.tls.yaml --env-file .env.prod logs --tail=50 nginx
```
证书路径错、证书不匹配域名都会在这里报出来。

**登录后刷新又变访客**
`COOKIE_SECURE=true` 但访问的是 HTTP，或者反过来。两者必须匹配。

---

## 不想等备案的替代方案

备案是合规要求，绕不过。如果急着要 HTTPS，只有两条路：

| 方案 | 代价 |
|---|---|
| 换成香港 VPS | 约 ¥24-30/月，无需备案，即时可用 HTTPS |
| Cloudflare Tunnel | 技术上可绕过，但**网站仍需备案**，存在合规风险，不建议 |

现在这套 99 元机器可以继续留作测试环境，或者到期不续。
