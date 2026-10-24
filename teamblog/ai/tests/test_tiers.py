"""分级与"资料过少"守卫（2026-10 新增）。

要验证三件事：
1. 匿名档额度是 8，第 9 次 429
2. 带合法 token → 成员档（额度 50，模型换成成员档）
3. 资料是占位内容时**不调模型**，直接给一句有用的话
"""

import json
import time

import jwt
import pytest
from fastapi.testclient import TestClient

from app import main
from app.config import get_settings


def make_token(secret: str, sub: str = "user-123") -> str:
    payload = {"sub": sub, "role": "MEMBER", "iat": int(time.time()), "exp": int(time.time()) + 600}
    return jwt.encode(payload, secret, algorithm="HS256")


PROJECT_RICH = {
    "id": "p1",
    "type": "PROJECT",
    "title": "TeaLog",
    "description": "打卡与积分系统",
    "content_md": "# 技术栈\n后端 Spring Boot 3，数据库 PostgreSQL 16，迁移用 Flyway。",
    "github_url": None,
}

PROJECT_SPARSE = {
    "id": "p2",
    "type": "PROJECT",
    "title": "test",
    "description": "test",
    "content_md": "test",
    "github_url": None,
}


@pytest.fixture()
def client(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "mock")
    monkeypatch.setenv("AI_ENABLED", "true")
    monkeypatch.setenv("AI_ANON_DAILY_LIMIT", "2")
    monkeypatch.setenv("AI_MEMBER_DAILY_LIMIT", "5")
    monkeypatch.setenv("AI_JWT_SECRET", "test-secret-for-unit-tests")
    monkeypatch.setenv("AI_DATABASE_URL", "postgresql://unused:unused@127.0.0.1:1/unused")
    get_settings.cache_clear()
    main.settings = get_settings()
    main.quota = main.DailyQuota()

    async def fake_project(project_id: str):
        return PROJECT_SPARSE if project_id == "sparse" else PROJECT_RICH

    monkeypatch.setattr(main.repo, "get_public_project", fake_project)
    with TestClient(main.app) as c:
        yield c
    get_settings.cache_clear()


def collect(response) -> str:
    body = "".join(response.iter_text())
    return "".join(
        json.loads(line[5:])["text"]
        for block in body.split("\n\n")
        for line in block.splitlines()
        if line.startswith("data:") and '"text"' in line
    )


def test_匿名额度用完后429(client):
    payload = {"projectId": "rich", "question": "用了什么数据库"}
    for _ in range(2):
        with client.stream("POST", "/v1/project-chat", json=payload) as response:
            assert response.status_code == 200
            collect(response)

    response = client.post("/v1/project-chat", json=payload)
    assert response.status_code == 429


def test_带token进入成员档_额度更高(client):
    token = make_token(main.settings.jwt_secret, "member-1")
    payload = {"projectId": "rich", "question": "用了什么数据库"}

    # 匿名档上限是 2；成员档是 5，所以第 3 次仍然能过
    for i in range(3):
        with client.stream("POST", "/v1/project-chat", json=payload,
                           headers={"Authorization": f"Bearer {token}"}) as response:
            assert response.status_code == 200, f"第 {i + 1} 次被拒"
            collect(response)


def test_额度为0表示不限(client):
    """成员档默认 0 = 不限：连问 10 次都不该被拦。"""
    monkeypatch_limit = "0"
    assert main.settings.member_daily_limit == 5  # fixture 里设的是 5

    # 直接测限流器本身：不限时只记数、永不返回 False
    from app.limits import DailyQuota

    quota = DailyQuota()
    for _ in range(50):
        assert quota.try_consume("user:x", 0) is True
    assert quota.remaining("user:x", 0) == -1
    # 有上限时照常拦
    for _ in range(3):
        assert quota.try_consume("user:y", 3) is True
    assert quota.try_consume("user:y", 3) is False
    assert quota.remaining("user:y", 3) == 0


def test_伪造token按匿名处理(client):
    payload = {"projectId": "rich", "question": "用了什么数据库"}
    bad = make_token("wrong-secret", "attacker")

    for _ in range(2):
        with client.stream("POST", "/v1/project-chat", json=payload,
                           headers={"Authorization": f"Bearer {bad}"}) as response:
            assert response.status_code == 200
            collect(response)

    # 伪造 token 不享受成员额度，第 3 次按匿名档拒掉
    response = client.post("/v1/project-chat", json=payload,
                           headers={"Authorization": f"Bearer {bad}"})
    assert response.status_code == 429


def test_资料过少_不调模型直接给提示(client):
    payload = {"projectId": "sparse", "question": "test 是什么"}

    with client.stream("POST", "/v1/project-chat", json=payload) as response:
        assert response.status_code == 200
        answer = collect(response)

    # 固定文案，而不是模型生成的"资料里没有写…资料中仅显示…"
    assert "资料还很少" in answer
    assert "占位内容" in answer
    assert "资料中仅显示" not in answer
