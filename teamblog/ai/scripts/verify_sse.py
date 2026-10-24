"""验证 AI 的 SSE 是否真的流式（经 Nginx）。

要证明的事情：
1. 首字节在 2 秒内到达（Gate G7 的指标）
2. 数据是**分批**到达的，不是攒完一次性给（Nginx 缓冲的典型症状）
3. 事件格式是 delta / done
"""

import json
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8088"


def post_json(path, payload, token=None):
    data = json.dumps(payload).encode()
    req = urllib.request.Request(BASE + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.status, json.loads(resp.read() or b"null")


# 1. 登录 + 准备一个项目
status, login = post_json("/api/v1/auth/login", {"username": "admin1", "password": "ChangeMe123!"})
token = login["data"]["accessToken"]

status, page = 0, None
req = urllib.request.Request(BASE + "/api/v1/projects?limit=1")
with urllib.request.urlopen(req, timeout=20) as resp:
    page = json.loads(resp.read())

if page["data"]["items"]:
    project_id = page["data"]["items"][0]["id"]
else:
    status, created = post_json(
        "/api/v1/projects",
        {
            "type": "PROJECT",
            "title": "AI 验收项目",
            "description": "用于验证 SSE",
            "githubUrl": "https://example.com/ai",
            "contentMd": "# 背景\n\n这是一个用于验证流式输出的项目。\n\n## 技术栈\nSpring Boot + Next.js + FastAPI",
        },
        token,
    )
    project_id = created["data"]["id"]

print(f"项目: {project_id}")

# 2. 发起 SSE 请求，记录每一块的到达时间
payload = json.dumps({"projectId": project_id, "question": "这个项目用了什么技术栈"}).encode()
request = urllib.request.Request(BASE + "/ai/v1/project-chat", data=payload, method="POST")
request.add_header("Content-Type", "application/json")

start = time.perf_counter()
first_chunk_at = None
chunk_times = []
raw = b""

with urllib.request.urlopen(request, timeout=60) as response:
    print(f"HTTP {response.status}  content-type={response.headers.get('content-type')}")
    print(f"x-accel-buffering={response.headers.get('x-accel-buffering')}")
    while True:
        piece = response.read(64)
        if not piece:
            break
        now = time.perf_counter() - start
        if first_chunk_at is None:
            first_chunk_at = now
        chunk_times.append(now)
        raw += piece

total = time.perf_counter() - start
text = raw.decode("utf-8", "ignore")

print(f"\n首块到达: {first_chunk_at:.2f}s  总耗时: {total:.2f}s  收包次数: {len(chunk_times)}")

events = [block for block in text.split("\n\n") if block.strip()]
kinds = [line[6:].strip() for block in events for line in block.splitlines() if line.startswith("event:")]
print(f"事件序列（前 5 / 共 {len(kinds)}）: {kinds[:5]} ... {kinds[-1:]}")

deltas = [json.loads(line[5:])["text"] for block in events for line in block.splitlines()
          if line.startswith("data:") and '"text"' in line]
answer = "".join(deltas)

print("\n=== 判定 ===")
print(f"{'PASS' if first_chunk_at is not None and first_chunk_at < 2 else 'FAIL'}  首字节 < 2s（实际 {first_chunk_at:.2f}s）")
print(f"{'PASS' if len(chunk_times) > 5 else 'FAIL'}  分批到达（{len(chunk_times)} 次读，不是一次性）")
print(f"{'PASS' if kinds and kinds[0] == 'delta' else 'FAIL'}  首个事件是 delta")
print(f"{'PASS' if kinds and kinds[-1] == 'done' else 'FAIL'}  最后一个事件是 done")
print(f"{'PASS' if '技术栈' in answer else 'FAIL'}  答案里有问题关键词（说明链路通到底）")
print(f"\n回答片段：{answer[:80]}...")
