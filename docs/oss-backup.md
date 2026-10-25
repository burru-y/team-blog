# OSS 异地备份

现在所有备份都在一台机器上：

```
数据库     → 系统盘
每日 dump  → Docker 卷
快照       → 同一块云盘
```

服务器整机故障、误删实例、云盘损坏，三份一起没。OSS 是把 dump 传到另一处。

---

## 第 1 步：开通 OSS 并买存储包

1. 阿里云控制台搜「对象存储 OSS」→ 开通（免费）
2. 左侧「资源包管理」→ 买**存储包**：标准 LRS，40 GB，一年约 ¥9
3. 用那 300 元优惠券结算

> 计费要点：**上传流量免费**，下载按量计费。我们只上传，几乎只花存储包的钱。

---

## 第 2 步：创建 Bucket

1. OSS 控制台 → Bucket 列表 → 创建
2. 关键选项：

| 项 | 填什么 | 原因 |
|---|---|---|
| Bucket 名称 | `yuxiao-backup`（全局唯一，可加后缀） | 名字只能用小写字母、数字、短横线 |
| **地域** | **华东1（杭州）** | 和 ECS 同地域，走内网，**不收流量费且更快** |
| 存储类型 | 标准存储 | 备份要随时能读，低频访问会加取回费 |
| 读写权限 | **私有** | 备份里有用户数据，绝不能公开 |
| 版本控制 | 关闭 | 备份文件本身带日期，不需要 |

3. 创建完记下 **Bucket 名称** 和 **地域 Endpoint**，形如：

```
oss-cn-hangzhou.aliyuncs.com
```

---

## 第 3 步：创建 RAM 子账号（不要用主账号 AccessKey）

用主账号的 AccessKey 等于把整个云的钥匙放在服务器上。正确做法是建一个只能读写这一个 Bucket 的子账号。

1. 控制台搜「RAM 访问控制」→ 用户 → 创建用户
2. 勾选「使用永久 AccessKey 访问」→ 创建
3. **立刻保存** AccessKey ID 和 Secret（Secret 只显示一次）
4. 给这个用户授权：选「自定义策略」，新建一条：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oss:PutObject", "oss:GetObject", "oss:ListObjects", "oss:DeleteObject"],
      "Resource": ["acs:oss:*:*:yuxiao-backup", "acs:oss:*:*:yuxiao-backup/*"]
    }
  ]
}
```

把两处 `yuxiao-backup` 换成你的 Bucket 名。

这条策略只给了这一个 Bucket 的读写删，**碰不到别的资源**。即使密钥泄露，损失面也有限。

---

## 第 4 步：服务器上装 ossutil

```bash
cd /tmp
wget https://gosspublic.alicdn.com/ossutil/1.7.19/ossutil64
chmod +x ossutil64
mv ossutil64 /usr/local/bin/ossutil
ossutil --version
```

配置（把三处换成你的）：

```bash
ossutil config \
  -e oss-cn-hangzhou.aliyuncs.com \
  -i 你的AccessKeyID \
  -k 你的AccessKeySecret
```

配置文件会写到 `/root/.ossutilconfig`，权限设成只有自己能读：

```bash
chmod 600 /root/.ossutilconfig
```

测试连通：

```bash
ossutil ls oss://yuxiao-backup/
```

看到 `Total Object Number: 0` 就说明通了。

---

## 第 5 步：上传备份

脚本已经写好，在 `teamblog/ops/backup-offsite.sh`。

先手动跑一次：

```bash
export OSS_BUCKET=yuxiao-backup
bash /root/teamblog/ops/backup-offsite.sh
```

预期输出：

```
== 上传最新的 dump ==
Succeed: Total num: 1, size: 61,000. OK num: 1(upload 1 files).

== 清理 OSS 上超过 30 天的备份 ==
没有需要清理的对象

== 当前 OSS 上的备份 ==
...
```

然后加定时任务，每天 03:30 跑（比 03:10 的本地备份晚 20 分钟，等它先写完）：

```bash
crontab -e
```

```cron
30 3 * * * OSS_BUCKET=yuxiao-backup bash /root/teamblog/ops/backup-offsite.sh >> /var/log/backup-offsite.log 2>&1
```

---

## 验证值得做一次

备份最怕「以为有，其实不能用」。

```bash
# 1. 从 OSS 拉一份下来
ossutil cp oss://yuxiao-backup/db/最新的.dump /tmp/verify.dump

# 2. 恢复到一个临时库
docker compose -f compose.prod.yaml --env-file .env.prod exec -T db \
  psql -U blog -d postgres -c "drop database if exists blog_verify; create database blog_verify;"

docker cp /tmp/verify.dump yuxiao-db-1:/tmp/verify.dump
docker compose -f compose.prod.yaml --env-file .env.prod exec -T db \
  pg_restore -U blog -d blog_verify --no-owner /tmp/verify.dump 2>&1 | tail -3

# 3. 数行数
docker compose -f compose.prod.yaml --env-file .env.prod exec -T db \
  psql -U blog -d blog_verify -c "select count(*) from users;"

# 4. 清理
docker compose -f compose.prod.yaml --env-file .env.prod exec -T db \
  psql -U blog -d postgres -c "drop database blog_verify;"
```

`users` 数量对得上，这条链路才算真的可用。

---

## 费用估算

| 项 | 用量 | 费用 |
|---|---|---|
| 存储包 40 GB | 备份一个月约 2 MB | ¥9/年（用券后免费） |
| 上传流量 | 每天几十 KB | 免费 |
| 下载流量 | 只在恢复时 | 几毛钱 |

**基本等于免费。** 真正有价值的是「服务器挂了数据还在」。

---

## 常见问题

**`AccessDenied`**
RAM 策略里的 Bucket 名和实际不一致，或者没授权。去 RAM 控制台看「权限策略」有没有绑上。

**上传很慢**
Bucket 地域和服务器不在同一地域，走了公网。杭州 ECS 配杭州 Bucket 才走内网。

**定时任务没执行**
cron 不读你 SSH 会话里的环境变量，所以脚本里用 `OSS_BUCKET=...` 显式传，别依赖 `export`。

**想换 Bucket**
改 crontab 里的 `OSS_BUCKET` 即可，脚本不写死。
