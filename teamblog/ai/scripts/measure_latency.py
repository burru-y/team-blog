import json
import ssl
import time
import urllib.error
import urllib.request

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
BASE = "https://localhost:8443"


def req(method, path, payload=None, token=None, timeout=120):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(BASE + path, data=data, method=method)
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=ctx) as response:
            return response.status, response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore")


def ask(project_id, question, token=None):
    start = time.perf_counter()
    status, body = req("POST", "/ai/v1/project-chat", {"projectId": project_id, "question": question}, token)
    elapsed = time.perf_counter() - start
    if status != 200:
        return status, elapsed, body[:80]
    text = "".join(
        json.loads(line[5:])["text"]
        for block in body.split("\n\n")
        for line in block.splitlines()
        if line.startswith("data:") and '"text"' in line
    )
    return status, elapsed, text


_, login = req("POST", "/api/v1/auth/login", {"username": "pwdemo", "password": "Third#2026"})
token = json.loads(login)["data"]["accessToken"]

_, page = req("GET", "/api/v1/projects?limit=20")
rich = [p for p in json.loads(page)["data"]["items"]
        if len((p.get("description") or "") + (p.get("contentMd") or "")) >= 20]
project_id = rich[0]["id"]

for label, tok in (("匿名", None), ("成员", token)):
    for i in range(2):
        status, elapsed, text = ask(project_id, "这个项目用了什么数据库？", tok)
        head = text[:50].replace("\n", " ")
        print("[%s 第%d次] %s  %.2fs  %s" % (label, i + 1, status, elapsed, head))
