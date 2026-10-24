import json
import os
import ssl
import tempfile
import urllib.error
import urllib.request

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

path = os.path.join(tempfile.gettempdir(), "cf-tunnel-url.txt")
BASE = open(path, encoding="utf-8").read().strip()
print("隧道:", BASE)


def req(method, p, payload=None, timeout=60):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(BASE + p, data=data, method=method)
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=ctx) as response:
            return response.status, response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore")


status, page = req("GET", "/api/v1/projects?limit=5")
print("项目接口:", status)
items = json.loads(page)["data"]["items"] if status == 200 else []

if items:
    pid = items[0]["id"]
    status, html = req("GET", "/projects/" + pid)
    has_entry = "问问 AI" in html
    print("详情页: %s | 含问问AI: %s | 长度: %d" % (status, has_entry, len(html)))

    status, body = req("POST", "/ai/v1/project-chat", {"projectId": pid, "question": "这个项目是什么"})
    print("匿名问 AI:", status, body[:150].replace("\n", " "))

print("ai health:", req("GET", "/ai/health")[0])
