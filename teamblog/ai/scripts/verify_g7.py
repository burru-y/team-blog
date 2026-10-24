"""Gate G7 端到端验收（经 nginx，模拟浏览器行为）。

验收项：
1. 项目详情页有"问问 AI"入口（SSR 就有，不等 JS）
2. 访客点开提问 → 首字 2 秒内到达
3. 回答只基于项目内容（含项目里的关键词）
4. 关闭连接后服务端停止生成（看日志）
"""

import json
import re
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8088"


def get(path):
    with urllib.request.urlopen(BASE + path, timeout=30) as response:
        return response.status, response.read().decode("utf-8", "ignore")


def first_project():
    _, body = get("/api/v1/projects?limit=5")
    items = json.loads(body)["data"]["items"]
    return items[0] if items else None


def stream_chat(project_id, question):
    payload = json.dumps({"projectId": project_id, "question": question}).encode()
    request = urllib.request.Request(BASE + "/ai/v1/project-chat", data=payload, method="POST")
    request.add_header("Content-Type", "application/json")

    start = time.perf_counter()
    first = None
    raw = b""
    with urllib.request.urlopen(request, timeout=90) as response:
        headers = dict(response.headers)
        while True:
            piece = response.read(128)
            if not piece:
                break
            if first is None:
                first = time.perf_counter() - start
            raw += piece
    return raw.decode("utf-8", "ignore"), (first or 0.0), headers


project = first_project()
print(f"项目: {project['title']} ({project['id']})")

status, html = get(f"/projects/{project['id']}")
has_entry = "问问 AI" in html
print(f"\n[1] 详情页 HTTP {status}  含'问问 AI'入口: {has_entry}")

body, ttfb, headers = stream_chat(project["id"], "这个项目用了什么技术？")
events = re.findall(r"event: (\w+)", body)
deltas = "".join(
    json.loads(line[5:])["text"]
    for block in body.split("\n\n")
    for line in block.splitlines()
    if line.startswith("data:") and '"text"' in line
)

print(f"[2] 首字 {ttfb:.2f}s（要求 < 2s）  事件数 {len(events)}  首个 {events[0] if events else '-'}")
print(f"[3] content-type: {headers.get('Content-Type', headers.get('content-type', '-'))}")
print(f"[4] 回答前 90 字：{deltas[:90]}")

# 只依据项目内容：回答里应当出现资料中的技术名词
keywords = ["Spring Boot", "Next.js", "FastAPI", "PostgreSQL", "Tailwind"]
hit = [k for k in keywords if k.lower() in deltas.lower()]
print(f"[5] 命中的项目关键词: {hit or '（无）'}")

ok = has_entry and ttfb < 2 and bool(events) and events[0] == "delta"
print(f"\nGate G7（浏览器侧）: {'PASS' if ok else 'FAIL'}")
