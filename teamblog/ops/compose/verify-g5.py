import json
import urllib.error
import urllib.request

# 账号密码从环境变量读：管理员改过密码后，脚本里写死就再也跑不通了。
#   $env:G5_USER="admin1"; $env:G5_PASSWORD="你的密码"; python verify-g5.py
import os
BASE = os.environ.get("G5_BASE", "http://localhost:8088")
USERNAME = os.environ.get("G5_USER", "admin1")
PASSWORD = os.environ.get("G5_PASSWORD", "")
results = []


def req(method, path, payload=None, token=None):
    data = json.dumps(payload).encode() if payload is not None else None
    r = urllib.request.Request(BASE + path, data=data, method=method)
    if payload is not None:
        r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            body = resp.read()
            try:
                return resp.status, json.loads(body or b"null")
            except Exception:
                return resp.status, body[:80]
    except urllib.error.HTTPError as e:
        # 错误响应也解析成 dict：否则调用方拿到的是 bytes，
        # 想判断 error.code（比如 409 ALREADY_CHECKED_IN）就只能做字符串匹配
        raw = e.read()
        try:
            return e.code, json.loads(raw or b"null")
        except Exception:
            return e.code, raw[:200]


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"  {'PASS' if ok else 'FAIL'}  {name}  {detail}")


print("[1] 健康检查（经 nginx）")
status, health = req("GET", "/api/v1/health")
check("health 200", status == 200, str(health)[:60])
if isinstance(health, dict):
    check("db UP", health.get("data", {}).get("db") == "UP")
    check("flyway 有版本", str(health.get("data", {}).get("flyway")) not in ("0", "None", ""))

print("\n[2] 首页（nginx -> web）")
status, body = req("GET", "/")
check("首页 200", status == 200, f"len={len(body) if isinstance(body, bytes) else 'n/a'}")

print("\n[3] 登录（nginx -> api）")
status, login = req("POST", "/api/v1/auth/login", {"username": USERNAME, "password": PASSWORD})
check("登录 200", status == 200)
token = login["data"]["accessToken"] if status == 200 else None
check("拿到 accessToken", bool(token))

print("\n[4] 公开接口")
status, users = req("GET", "/api/v1/users?limit=3")
check("成员列表 200", status == 200)
status, feed = req("GET", "/api/v1/projects?limit=3")
check("成果流 200", status == 200)

print("\n[5] 写入（打卡 + 发内容）")
status, checkin = req("POST", "/api/v1/checkins", {}, token)
# 409 ALREADY_CHECKED_IN 是**正确的幂等行为**：同一天第二次打卡本来就该被拒。
# 验收脚本要区分"产品坏了"和"今天已经打过卡了"。
if status == 201:
    check("打卡 201", True, f"streak={checkin['data']['streak']}")
elif status == 409 and isinstance(checkin, dict) and checkin.get("error", {}).get("code") == "ALREADY_CHECKED_IN":
    check("打卡幂等（今天已打过，返回 409 ALREADY_CHECKED_IN）", True,
          str(checkin.get("error", {}).get("message"))[:40])
else:
    check("打卡", False, str(checkin)[:80])
status, log = req("POST", "/api/v1/learning-logs",
                  {"type": "NOTE", "title": "G5 验收：容器内发布", "contentMd": "# 在容器里写的第一篇"},
                  token)
check("发内容 201", status == 201, f"计分={log['data']['counted']}" if status == 201 else str(log)[:80])

print("\n[6] 内容立刻可见（读回）")
if status == 201:
    log_id = log["data"]["log"]["id"]
    status, detail = req("GET", f"/api/v1/learning-logs/{log_id}")
    check("详情 200", status == 200)
    check("标题正确", isinstance(detail, dict) and detail["data"]["title"] == "G5 验收：容器内发布")

print("\n[7] 静态资源路径（/uploads 由 nginx 直接服务）")
status, _ = req("GET", "/uploads/not-exist.png")
check("不存在的上传文件返回 404（说明 location 生效）", status == 404, f"HTTP {status}")

print("\n[8] 未登录访问需登录接口被拒")
status, _ = req("GET", "/api/v1/checkins/status")
check("401", status == 401, f"HTTP {status}")

failed = [r for r in results if not r[1]]
print(f"\n结果：{len(results) - len(failed)}/{len(results)} 通过")
