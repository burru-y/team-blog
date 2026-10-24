"""摘要链路的端到端验证：登录 → 建学习内容 → 生成摘要 → 回读。"""

import json
import ssl
import urllib.error
import urllib.request

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
B = "https://localhost:8443"


def req(method, path, payload=None, token=None, timeout=90):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(B + path, data=data, method=method)
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=ctx) as response:
            return response.status, response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore")


status, body = req("POST", "/api/v1/auth/login", {"username": "pwdemo", "password": "Third#2026"})
token = json.loads(body)["data"]["accessToken"]

status, body = req("GET", "/api/v1/learning-logs?username=pwdemo", token=token)
logs = json.loads(body)["data"]
print("列表状态:", status, "| 条数:", len(logs))
if logs:
    print("字段:", sorted(logs[0].keys()))

if not logs:
    status, body = req(
        "POST",
        "/api/v1/learning-logs",
        {
            "type": "NOTE",
            "title": "摘要功能验证",
            "contentMd": "今天学习了 Spring Boot 的依赖注入。构造器注入是推荐做法，因为它能保证依赖不为空。"
                         "循环依赖时构造器注入会直接报错，setter 注入则允许延迟解析。",
        },
        token,
    )
    print("新建学习内容:", status)
    print("返回:", body[:200])
    status, body = req("GET", "/api/v1/learning-logs?username=pwdemo", token=token)
    logs = json.loads(body)["data"]

log = logs[0]
print("目标:", log.get("title"), "| 已有摘要:", bool(log.get("summaryMd")))

status, body = req("POST", f"/api/v1/learning-logs/{log['id']}/summarize", token=token)
print("生成摘要:", status)
if status == 200:
    print("摘要:", json.loads(body)["data"].get("summary", "")[:150])

status, body = req("GET", f"/api/v1/learning-logs/{log['id']}", token=token)
saved = json.loads(body)["data"].get("summaryMd")
print("回读已保存:", bool(saved))
