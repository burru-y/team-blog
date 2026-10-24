"""通过 Cloudflare 隧道，用"外网身份"验收整套系统。

和本地验收的区别：请求真的出了公网、经 Cloudflare 再回来，
能验证证书、Cookie、SSE、真实延迟。
"""

import json
import time
import urllib.error
import urllib.request

import sys

# 地址从命令行或临时文件读：免费隧道每次启动都会变，写死就失效了
BASE = (
    sys.argv[1] if len(sys.argv) > 1
    else __import__("pathlib").Path(__import__("tempfile").gettempdir(), "cf-tunnel-url.txt").read_text(encoding="utf-8").strip()
)


def req(method, path, payload=None, token=None, timeout=60):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method)
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", "ignore"), dict(response.headers)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore"), dict(exc.headers)


def ms(start):
    return (time.perf_counter() - start) * 1000


start = time.perf_counter()
status, body, _ = req("GET", "/api/v1/health")
print("[1] 健康检查  %s  %s  (%.0f ms)" % (status, body.strip()[:55], ms(start)))

start = time.perf_counter()
status, body, _ = req("GET", "/")
has_brand = "TeamBlog" in body
print("[2] 首页      %s  len=%d  含 TeamBlog=%s  (%.0f ms)" % (status, len(body), has_brand, ms(start)))

status, body, _ = req("GET", "/api/v1/projects?limit=2")
items = json.loads(body)["data"]["items"] if status == 200 else []
print("[3] 成果流    %s  条数=%d" % (status, len(items)))

status, body, _ = req("GET", "/ai/health")
print("[4] AI 健康   %s  %s" % (status, body.strip()[:50]))

status, body, headers = req("POST", "/api/v1/auth/login",
                            {"username": "pwdemo", "password": "Third#2026"})
cookie = headers.get("Set-Cookie", "")
print("[5] 成员登录  %s  Cookie 带 Secure=%s" % (status, "Secure" in cookie))

if items:
    start = time.perf_counter()
    status, body, _ = req("POST", "/ai/v1/project-chat",
                          {"projectId": items[0]["id"], "question": "这个项目用了什么技术？"},
                          timeout=90)
    first = time.perf_counter() - start
    answer = "".join(
        json.loads(line[5:])["text"]
        for block in body.split("\n\n")
        for line in block.splitlines()
        if line.startswith("data:") and '"text"' in line
    )
    print("[6] AI 问答   %s  首字 %.2fs  回答前 45 字：%s" % (status, first, answer[:45]))
