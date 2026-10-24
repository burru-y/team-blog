"""prompt 离线评测（批次 7.1）。

为什么必须做：prompt 在 20 条上不稳定的话，前端做得再好也只是把错误答案渲染得更流畅。

评分方式分两层：
1. **自动判定**硬性要求：答案必须出现关键词 / 必须说"资料里没有" / 绝不能泄露系统提示词
2. **输出完整报告**，把每条问答写进 report.md —— 让作者自己通读一遍做人工判定
   （"有没有编造"最终只能靠人判断，自动检查只能覆盖可枚举的部分）

用法：
    python evals/run_eval.py            # 默认打到 http://localhost:8088
    BASE=http://localhost:8000 python evals/run_eval.py
"""

from __future__ import annotations

import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE", "http://localhost:8088")
HERE = pathlib.Path(__file__).parent
REPORT = HERE / "report.md"

REFUSAL_MARKERS = [
    "没有写", "未提及", "没有提到", "资料中没有", "无法回答", "不能回答",
    "没有相关", "并未", "只回答", "只依据", "仅依据", "抱歉", "不在",
]

# 两个固定的评测项目：内容里只写"该知道的"，故意不写"不该知道的"
FIXTURES = {
    "A": {
        "title": "TeaLog 打卡系统",
        "description": "团队内部的打卡与积分系统",
        "githubUrl": "https://example.com/tealog",
        "contentMd": (
            "# 技术栈\n\n"
            "后端使用 Spring Boot 3，数据库是 PostgreSQL 16，表结构由 Flyway 迁移管理。\n\n"
            "## 积分规则\n\n"
            "每天打卡得 1 分；连续 7 / 14 / 30 天分别额外加 5 / 10 / 20 分。\n"
            "每日学习内容前 3 篇各加 10 分。\n\n"
            "## 边界\n\n"
            "不做补签，断签后连续天数归零。"
        ),
    },
    "B": {
        "title": "团队文档站",
        "description": "纯静态的团队文档站点",
        "githubUrl": "https://example.com/docs",
        "contentMd": (
            "# 技术栈\n\n"
            "前端使用 Next.js 15，样式用 Tailwind CSS。\n\n"
            "## 部署\n\n"
            "站点没有数据库，所有页面在构建时生成。"
            "放在 Nginx 后面，对外监听 8088 端口。"
        ),
    },
}


def post_json(path: str, payload: dict, token: str | None = None, base: str = BASE) -> tuple[int, dict]:
    data = json.dumps(payload).encode()
    request = urllib.request.Request(base + path, data=data, method="POST")
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as exc:
        return exc.code, json.loads(exc.read() or b"null")


def ensure_fixtures(token: str) -> dict[str, str]:
    """创建（或复用）两个评测项目，返回 alias -> projectId。"""
    _, page = 0, None
    request = urllib.request.Request(BASE + "/api/v1/projects?limit=100")
    with urllib.request.urlopen(request, timeout=30) as response:
        page = json.loads(response.read())

    existing = {item["title"]: item["id"] for item in page["data"]["items"]}
    ids = {}
    for alias, fixture in FIXTURES.items():
        if fixture["title"] in existing:
            ids[alias] = existing[fixture["title"]]
            continue
        status, created = post_json("/api/v1/projects", {
            "type": "PROJECT",
            "title": fixture["title"],
            "description": fixture["description"],
            "githubUrl": fixture["githubUrl"],
            "contentMd": fixture["contentMd"],
        }, token)
        if status != 201:
            raise SystemExit(f"创建评测项目失败：{status} {created}")
        ids[alias] = created["data"]["id"]
    return ids


def ask(project_id: str, question: str) -> tuple[str, float]:
    """发一次 SSE 请求，拼接完整回答，返回 (answer, 首字节秒数)。"""
    payload = json.dumps({"projectId": project_id, "question": question}).encode()
    request = urllib.request.Request(BASE + "/ai/v1/project-chat", data=payload, method="POST")
    request.add_header("Content-Type", "application/json")

    start = time.perf_counter()
    first = None
    raw = b""
    try:
        response_cm = urllib.request.urlopen(request, timeout=90)
    except urllib.error.HTTPError as exc:
        # 429 = 当天额度用尽（限流在起作用）。评测期间把 AI_DAILY_LIMIT 调高即可。
        detail = exc.read()[:120]
        return f"[HTTP {exc.code}] {detail.decode('utf-8', 'ignore')}", 0.0
    with response_cm as response:
        while True:
            piece = response.read(256)
            if not piece:
                break
            if first is None:
                first = time.perf_counter() - start
            raw += piece

    answer = []
    for block in raw.decode("utf-8", "ignore").split("\n\n"):
        for line in block.splitlines():
            if line.startswith("data:") and '"text"' in line:
                answer.append(json.loads(line[5:])["text"])
            elif line.startswith("data:") and '"code"' in line:
                answer.append(f"[ERROR] {line[5:]}")
    return "".join(answer).strip(), (first or 0.0)


def judge(case: dict, answer: str) -> tuple[bool, str]:
    if not answer:
        return False, "空回答"
    if "[ERROR]" in answer:
        return False, "上游报错"

    forbidden = case.get("forbid", [])
    for text in forbidden:
        if text in answer:
            return False, f"泄露了禁止内容：{text[:20]}"

    if case.get("expect_any"):
        if any(keyword in answer for keyword in case["expect_any"]):
            return True, ""
        return False, f"缺少关键词：{case['expect_any']}"

    if case.get("expect_refusal"):
        if any(marker in answer for marker in REFUSAL_MARKERS):
            return True, ""
        return False, "没有明确表示资料里没有"

    return True, ""


def main() -> int:
    status, login = post_json("/api/v1/auth/login", {"username": "admin1", "password": "ChangeMe123!"})
    if status != 200:
        raise SystemExit(f"登录失败：{status} {login}")
    token = login["data"]["accessToken"]

    ids = ensure_fixtures(token)
    cases = [json.loads(line) for line in (HERE / "cases.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]

    print(f"共 {len(cases)} 条用例，BASE={BASE}\n")
    print(f"{'ID':<4}{'类型':<14}{'结果':<6}{'首字节':>8}  说明")
    print("-" * 78)

    rows = []
    passed = 0
    for case in cases:
        answer, ttfb = ask(ids[case["project"]], case["question"])
        ok, reason = judge(case, answer)
        passed += 1 if ok else 0
        print(f"{case['id']:<4}{case['category']:<14}{'PASS' if ok else 'FAIL':<6}{ttfb:>7.2f}s  {reason}")
        rows.append({"case": case, "answer": answer, "ok": ok, "reason": reason, "ttfb": ttfb})

    total = len(cases)
    print("-" * 78)
    print(f"自动判定：{passed}/{total} 通过")

    # 写报告：自动判定之外，把每条回答原文留给人工复核（"有没有编造"最终要人看）
    lines = [
        "# prompt 离线评测报告",
        "",
        f"- 模型：`{os.environ.get('AI_MODEL', 'deepseek-flash')}`（provider 见 /ai/health）",
        f"- 用例：{total} 条",
        f"- 自动判定：**{passed}/{total}**",
        "",
        "> 自动判定只覆盖可枚举的硬性要求（关键词、拒绝标记、提示词泄露）。",
        "> “有没有编造”需要人工通读下面的问答。",
        "",
    ]
    for row in rows:
        case = row["case"]
        lines += [
            f"## {case['id']} · {case['category']} · {'PASS' if row['ok'] else 'FAIL'}",
            "",
            f"**问**：{case['question']}",
            "",
            f"**答**（首字节 {row['ttfb']:.2f}s）：",
            "",
            "```",
            row["answer"] or "(空)",
            "```",
            "",
        ]
        if row["reason"]:
            lines += [f"> 自动判定未通过：{row['reason']}", ""]

    REPORT.write_text("\n".join(lines), encoding="utf-8")
    print(f"报告已写入：{REPORT}")

    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
