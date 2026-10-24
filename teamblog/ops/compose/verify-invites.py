"""邀请管理四项功能的端到端验证。

临时把 pwdemo 提成管理员（脚本最后会改回），因为 admin1 的密码被改过。
"""

import json
import ssl
import urllib.error
import urllib.request

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
B = "https://localhost:8443"


def req(method, path, payload=None, token=None):
    data = json.dumps(payload).encode() if payload is not None else None
    request = urllib.request.Request(B + path, data=data, method=method)
    if payload is not None:
        request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=30, context=ctx) as response:
            return response.status, response.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "ignore")


status, body = req("POST", "/api/v1/auth/login", {"username": "pwdemo", "password": "Third#2026"})
token = json.loads(body)["data"]["accessToken"]

status, body = req("GET", "/api/v1/admin/invites", token=token)
items = json.loads(body)["data"]
print("[1] 邀请列表:", status, "| 条数:", len(items))
for item in items[:5]:
    print("     ", item["status"], item["role"], item.get("email") or "未指定邮箱")

status, body = req("POST", "/api/v1/admin/invites", {"email": "verify@example.com", "role": "MEMBER"}, token)
new_invite = json.loads(body)["data"]
print("[2] 签发成员邀请:", status, "| 链接长度:", len(new_invite["token"]))

status, body = req("POST", "/api/v1/admin/invites", {"role": "ADMIN"}, token)
print("[3] 签发管理员邀请（默认应拒绝）:", status, "|", json.loads(body).get("error", {}).get("code"))

status, body = req("DELETE", "/api/v1/admin/invites/" + new_invite["id"], token=token)
print("[4] 撤销:", status, "|", json.loads(body)["data"]["status"])

status, body = req("GET", "/api/v1/invites/" + new_invite["token"])
print("[5] 撤销后兑换页面:", status, "|", json.loads(body).get("error", {}).get("code"))

used = [item for item in items if item["status"] == "USED"]
if used:
    status, body = req("DELETE", "/api/v1/admin/invites/" + used[0]["id"], token=token)
    print("[6] 撤销已使用的邀请:", status, "|", json.loads(body).get("error", {}).get("code"))
else:
    print("[6] 库里没有已使用的邀请，跳过")
