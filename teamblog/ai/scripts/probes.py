"""对比访客档与成员档对同一个问题的回答（验证"成员不加限制"）。

用法：python probes.py [问题...]
"""

import json
import ssl
import sys
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


def ask(project_id, question, token=None):
    status, body = req("POST", "/ai/v1/project-chat",
                       {"projectId": project_id, "question": question}, token)
    if status != 200:
        return status, body[:100]
    text = "".join(
        json.loads(line[5:])["text"]
        for block in body.split("\n\n")
        for line in block.splitlines()
        if line.startswith("data:") and '"text"' in line
    )
    return status, text


_, login = req("POST", "/api/v1/auth/login", {"username": "pwdemo", "password": "Third#2026"})
token = json.loads(login)["data"]["accessToken"]
_, page = req("GET", "/api/v1/projects?limit=20")
project_id = json.loads(page)["data"]["items"][0]["id"]

questions = sys.argv[1:] or ["Spring Boot 3 是什么", "这个项目用了什么数据库"]
for question in questions:
    for label, tok in (("访客", None), ("成员", token)):
        status, answer = ask(project_id, question, tok)
        print("\n[%s] %s  -> %s" % (label, question, status))
        print(answer[:220].strip())
