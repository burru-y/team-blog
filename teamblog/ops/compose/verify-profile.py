"""验证个人主页的三处改动：资料修改、成果列表、作者筛选。"""

import json
import ssl
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
        with urllib.request.urlopen(request, timeout=30, context=ctx) as response:
            return response.status, response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore")


_, login = req("POST", "/api/v1/auth/login", {"username": "pwdemo", "password": "Third#2026"})
token = json.loads(login)["data"]["accessToken"]
me = json.loads(req("GET", "/api/v1/me", token=token)[1])["data"]
print("当前用户:", me["username"], "| bio:", me.get("bio"))

status, body = req("PATCH", "/api/v1/me",
                   {"bio": "在学习 Spring Boot 与 Next.js", "githubUrl": "https://github.com/burru-y",
                    "emailPublic": True}, token)
print("修改资料:", status, "| bio:", json.loads(body)["data"]["bio"])

status, body = req("PATCH", "/api/v1/me",
                   {"githubUrl": "javascript:alert(1)"}, token)
print("非法链接:", status, json.loads(body).get("error", {}).get("code"))

status, body = req("GET", "/api/v1/projects?author=" + me["username"])
items = json.loads(body)["data"]["items"]
print("按作者筛选:", status, "| 条数:", len(items), "| 全部属于本人:",
      all(item["ownerUsername"] == me["username"] for item in items))

status, body = req("GET", "/api/v1/projects?author=nobody_zzz")
print("不存在的作者:", status, "| 条数:", len(json.loads(body)["data"]["items"]))

status, html = req("GET", "/u/" + me["username"])
checks = {
    "含编辑资料按钮": "编辑资料" in html,
    "含数据卡": "打卡天数" in html,
    "含成果区块": ">成果<" in html or "成果" in html,
    "等级不重复": html.count(f"Lv{me['level']}") >= 1,
}
print("个人页:", status, checks)
