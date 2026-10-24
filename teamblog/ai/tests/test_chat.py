import json
import hmac
import hashlib

import pytest
from fastapi.testclient import TestClient

from app import main
from app.config import get_settings


@pytest.fixture()
def client(monkeypatch):
    # 每个测试用新的 settings 与 quota，避免互相污染
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.setenv("AI_ENABLED", "true")
    monkeypatch.setenv("AI_ANON_DAILY_LIMIT", "2")
    monkeypatch.setenv("AI_DATABASE_URL", "postgresql://unused:unused@127.0.0.1:1/unused")
    get_settings.cache_clear()
    main.settings = get_settings()
    main.quota = main.DailyQuota()

    async def fake_project(project_id: str):
        # 内容要给够：少于 min_usable_content_chars 会走"空资料守卫"，
        # 那样就测不到模型流式这条路径了
        return {
            "id": project_id,
            "type": "PROJECT",
            "title": "测试项目",
            "description": "用于验证流式输出与事件格式的测试项目",
            "content_md": "# 正文\n\n这个项目用了 Spring Boot 与 PostgreSQL，部署在 Nginx 后面。",
            "github_url": None,
        }

    monkeypatch.setattr(main.repo, "get_public_project", fake_project)
    with TestClient(main.app) as c:
        yield c
    get_settings.cache_clear()


def parse_sse(raw: str) -> list[tuple[str, str]]:
    """把 SSE 文本解析成 (event, data) 列表，忽略注释行。"""
    events: list[tuple[str, str]] = []
    for block in raw.split("\n\n"):
        block = block.strip()
        if not block or block.startswith(":"):
            continue
        event, data = "", ""
        for line in block.splitlines():
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                data = line[5:].strip()
        if event:
            events.append((event, data))
    return events


def test_health(client):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["provider"] == "mock"


def test_project_chat_streams_deltas_then_done(client):
    with client.stream(
        "POST", "/v1/project-chat", json={"projectId": "00000000-0000-7000-8000-000000000001", "question": "这个项目做什么"}
    ) as response:
        assert response.status_code == 200
        assert response.headers["content-type"].startswith("text/event-stream")
        # 关键头：没有它，Nginx 会把整个响应缓冲成一次性输出
        assert response.headers["x-accel-buffering"] == "no"
        body = "".join(response.iter_text())

    events = parse_sse(body)
    assert events, "应当有 SSE 事件"
    assert events[0][0] == "delta"
    assert events[-1][0] == "done"
    joined = "".join(json.loads(data)["text"] for name, data in events if name == "delta")
    assert "这个项目做什么" in joined


def test_empty_question_rejected(client):
    response = client.post(
        "/v1/project-chat", json={"projectId": "00000000-0000-7000-8000-000000000001", "question": "   "}
    )
    assert response.status_code == 400


def test_daily_quota(client):
    payload = {"projectId": "00000000-0000-7000-8000-000000000001", "question": "问题"}
    for _ in range(2):
        with client.stream("POST", "/v1/project-chat", json=payload) as response:
            assert response.status_code == 200
            "".join(response.iter_text())
    response = client.post("/v1/project-chat", json=payload)
    assert response.status_code == 429


def test_summarize_requires_valid_hmac(client):
    secret = main.settings.internal_hmac_secret.encode()
    body = json.dumps({"content": "今天学了 JPA 的一级缓存"}, ensure_ascii=False)

    bad = client.post("/v1/summarize", content=body, headers={"X-Internal-Signature": "wrong"})
    assert bad.status_code == 403

    signature = hmac.new(secret, "今天学了 JPA 的一级缓存".encode(), hashlib.sha256).hexdigest()
    ok = client.post("/v1/summarize", json={"content": "今天学了 JPA 的一级缓存"},
                     headers={"X-Internal-Signature": signature})
    assert ok.status_code == 200
    assert "mock 摘要" in ok.json()["summary"]


def test_disabled_flag_returns_503(client, monkeypatch):
    monkeypatch.setattr(main.settings, "enabled", False)
    response = client.post(
        "/v1/project-chat", json={"projectId": "00000000-0000-7000-8000-000000000001", "question": "hi"}
    )
    assert response.status_code == 503
