"""模型调用：一个接口，两种实现。

为什么要抽象：没有 API key 时整条 SSE 链路（含 Nginx 缓冲）都要能验；
等 key 到位，只换环境变量 `AI_PROVIDER=deepseek`，端点代码一行不改。

mock 实现不是"随便返回一段话"：它把项目标题和问题拼进回答，这样能一眼看出
请求真的到了模型层（而不是被缓存或占位符糊住）。
"""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator

import httpx

from .config import Settings


class ModelError(RuntimeError):
    """上游模型不可用。路由层会把它翻译成 502 / SSE error 事件。"""


async def stream_answer(settings: Settings, prompt: str, model: str | None = None, max_tokens: int = 600) -> AsyncIterator[str]:
    """逐块产出文本。调用方负责包成 SSE 事件。

    model 由调用方按身份决定（匿名用便宜模型、成员用强模型），
    provider 层不关心"为什么用这个模型"。
    """
    if settings.provider == "mock":
        async for chunk in _mock_stream(prompt):
            yield chunk
        return

    async for chunk in _openai_compatible_stream(settings, prompt, model or settings.anon_model, max_tokens):
        yield chunk


async def complete(settings: Settings, prompt: str) -> str:
    """非流式（摘要用）。"""
    if settings.provider == "mock":
        await asyncio.sleep(0.05)
        return _mock_summary(prompt)

    payload = {
        # 摘要只由 Spring 在成员操作时触发，用成员档模型
        "model": settings.member_model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.2,
        "max_tokens": 400,
        "stream": False,
    }
    try:
        async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
            response = await client.post(
                f"{settings.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.api_key}"},
                json=payload,
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"].strip()
    except Exception as exc:  # noqa: BLE001 - 统一翻译成 ModelError
        raise ModelError(str(exc)) from exc


async def _openai_compatible_stream(settings: Settings, prompt: str, model: str, max_tokens: int) -> AsyncIterator[str]:
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.3,
        "max_tokens": max_tokens,
        # 官方参数：{"thinking": {"type": "enabled|disabled"}}
        "thinking": {"type": settings.thinking},
        "stream": True,
    }
    try:
        async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
            async with client.stream(
                "POST",
                f"{settings.base_url.rstrip('/')}/chat/completions",
                headers={"Authorization": f"Bearer {settings.api_key}"},
                json=payload,
            ) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        return
                    try:
                        import json

                        chunk = json.loads(data)
                        delta = chunk["choices"][0].get("delta", {}).get("content")
                    except (ValueError, KeyError, IndexError):
                        continue
                    if delta:
                        yield delta
    except ModelError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise ModelError(str(exc)) from exc


async def _mock_stream(prompt: str) -> AsyncIterator[str]:
    """把 prompt 里的问题回显出来，分块发送——用来验证 SSE 真的在流。"""
    question = "（未识别到问题）"
    for marker in ("【访客问题】", "【内容】"):
        if marker in prompt:
            question = prompt.split(marker, 1)[1].strip()[:120]
            break

    answer = (
        "（mock 模式）这是一段用于验证链路的回答。\n\n"
        f"你的问题是：{question}\n\n"
        "如果这段文字是「逐字出现」的，说明 Nginx 的 proxy_buffering 已关闭、SSE 正常。"
        "配置真实模型后，这里会换成基于项目内容的回答。"
    )
    for char in answer:
        await asyncio.sleep(0.01)
        yield char


def _mock_summary(prompt: str) -> str:
    body = prompt.split("【内容】", 1)[-1].strip().replace("\n", " ")[:80]
    return f"（mock 摘要）这段内容主要讲了：{body}……"
