"""验证：成员额度不限（连问 10 次不被拦），访客仍是 8 次/天/IP。"""

import json
import ssl
import time
import urllib.error
import urllib.request

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
BASE = "https://localhost:8443"


def req(method, path, payload=None, token=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method)
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=90, context=ctx) as response:
            return response.status, response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore")


_, login = req("POST", "/api/v1/auth/login", {"username": "pwdemo", "password": "Third#2026"})
token = json.loads(login)["data"]["accessToken"]

_, page = req("GET", "/api/v1/projects?limit=20")
items = json.loads(page)["data"]["items"]
rich = [p for p in items if len((p.get("description") or "") + (p.get("contentMd") or "")) >= 20]
project_id = rich[0]["id"]

start = time.perf_counter()
codes = []
for i in range(10):
    status, _ = req("POST", "/ai/v1/project-chat",
                    {"projectId": project_id, "question": "这个项目用了什么数据库（第%d次）" % (i + 1)},
                    token)
    codes.append(status)

print("成员连问 10 次:", codes)
print("全部 200:", all(code == 200 for code in codes), "| 总耗时 %.1fs" % (time.perf_counter() - start))

status, body = req("POST", "/ai/v1/project-chat", {"projectId": project_id, "question": "匿名还能问吗"})
print("匿名状态:", status, body[:80].replace("\n", " "))
