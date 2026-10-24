"""FastAPI 入口：项目问答（SSE）+ 摘要（内部端点）。

三个设计点：
1. SSE 事件只有三种（delta / done / error），前端只认这三种；
2. 每 15 秒发一次 `: ping` 注释行，防止中间代理把空闲连接掐掉；
3. 客户端断开后停止调用模型——否则用户关了页面，token 还在烧。
"""

from __future__ import annotations

import asyncio
import hashlib
import hmac
import json
import logging
from collections.abc import AsyncIterator
from typing import Annotated
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import AliasChoices, BaseModel, Field

from . import prompts
from .config import Settings, get_settings
from .db import ProjectRepository
from .limits import DailyQuota
from .provider import ModelError, complete, stream_answer

# 必须显式配置：uvicorn 只配它自己的 logger，我们自定义的 logger 默认继承 root（WARNING），
# INFO 级别的"客户端断开"会完全看不到——排查断连问题时会被误导成"没断"。
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("teamblog.ai")

settings: Settings = get_settings()
repo = ProjectRepository(settings)
quota = DailyQuota(settings.daily_limit)


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        await repo.connect()
        logger.info("已连接数据库（只读视图）")
    except Exception as exc:  # noqa: BLE001
        # 数据库连不上不阻止服务启动：/health 会把它暴露出来，摘要端点仍可用
        logger.warning("数据库连接失败：%s", exc)
    yield
    await repo.close()


app = FastAPI(title="TeamBlog AI", version="0.1.0", lifespan=lifespan)


class ChatRequest(BaseModel):
    # 前端习惯用 camelCase（项目里其他地方也是），这里显式声明两个名字都接受。
    # 用 AliasChoices 而不是 Field(alias=...)：后者在 pydantic v2 会报
    # "alias has no effect in the context it was used"，而且行为依赖模型配置。
    project_id: Annotated[str, Field(validation_alias=AliasChoices("projectId", "project_id"))]
    question: str


class SummarizeRequest(BaseModel):
    content: str


def _client_key(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for")
    if forwarded:
        return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


@app.get("/health")
async def health() -> dict:
    return {"ok": True, "provider": settings.provider, "enabled": settings.enabled}


@app.post("/v1/project-chat")
async def project_chat(payload: ChatRequest, request: Request) -> StreamingResponse:
    if not settings.enabled:
        raise HTTPException(status_code=503, detail="AI 功能已关闭")

    question = payload.question.strip()
    if not question:
        raise HTTPException(status_code=400, detail="问题不能为空")
    if len(question) > settings.max_question_chars:
        raise HTTPException(status_code=400, detail=f"问题太长（上限 {settings.max_question_chars} 字）")

    project = await repo.get_public_project(payload.project_id)
    if project is None:
        raise HTTPException(status_code=404, detail="项目不存在或未公开")

    if not quota.try_consume(_client_key(request)):
        raise HTTPException(status_code=429, detail="今天的提问次数已用完")

    prompt = prompts.project_chat_prompt(
        title=project["title"],
        type_=project["type"],
        description=project["description"],
        content_md=project["content_md"],
        question=question,
    )

    async def event_stream() -> AsyncIterator[bytes]:
        # X-Accel-Buffering 由路由统一加；这里是内容
        def sse(event: str, data: dict) -> bytes:
            return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n".encode()

        stopped = False
        try:
            async for chunk in _with_heartbeat(stream_answer(settings, prompt), request):
                if await request.is_disconnected():
                    stopped = True
                    logger.info("客户端断开，停止生成")
                    break
                if chunk == "":
                    # 15 秒没吐字：发一个 SSE 注释行保活，前端会忽略它
                    yield b": ping\n\n"
                    continue
                yield sse("delta", {"text": chunk})
            if not stopped:
                yield sse("done", {"remaining": quota.remaining(_client_key(request))})
        except ModelError as exc:
            # 客户端先断开时，上游流会被取消并表现为 ModelError。
            # 必须区分这两种情况：一个是正常关闭，一个是真故障——
            # 混在一起记成"模型调用失败"，排查时会去查模型，方向就错了。
            if await request.is_disconnected():
                logger.info("客户端断开，停止生成（已停止调用模型）")
                return
            logger.warning("模型调用失败：%r", exc)
            yield sse("error", {"code": "UPSTREAM_FAILED", "message": "AI 服务暂时不可用，请稍后再试"})
        except asyncio.CancelledError:
            # 客户端断开时 uvicorn 会取消这个生成器：正常路径，不要记成错误
            logger.info("生成被取消（客户端断开）")
            raise

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-store",
            "X-Accel-Buffering": "no",   # 关键：告诉 Nginx 不要缓冲
            "Connection": "keep-alive",
        },
    )


async def _with_heartbeat(source: AsyncIterator[str], request: Request) -> AsyncIterator[str]:
    """在等待模型输出的间隙插入心跳，避免长连接被中间层掐断。

    实现方式：给下一个 chunk 设 15 秒超时；超时就产出一个空字符串（调用方跳过），
    同时由外层发送 `: ping`。这里用空串表示"该发心跳了"。
    """
    iterator = source.__aiter__()
    while True:
        try:
            chunk = await asyncio.wait_for(iterator.__anext__(), timeout=15)
        except StopAsyncIteration:
            return
        except asyncio.TimeoutError:
            yield ""      # 调用方据此发 ping
            continue
        yield chunk


@app.post("/v1/summarize")
async def summarize(payload: SummarizeRequest, x_internal_signature: str = Header(default="")) -> JSONResponse:
    """内部端点：只允许 Spring 带 HMAC 调用（Nginx 会挡住外网直连）。"""
    expected = hmac.new(
        settings.internal_hmac_secret.encode(),
        payload.content.encode(),
        hashlib.sha256,
    ).hexdigest()
    if not hmac.compare_digest(expected, x_internal_signature):
        raise HTTPException(status_code=403, detail="签名无效")

    if not settings.enabled:
        raise HTTPException(status_code=503, detail="AI 功能已关闭")
    if not payload.content.strip():
        raise HTTPException(status_code=400, detail="内容不能为空")

    try:
        summary = await complete(settings, prompts.summary_prompt(payload.content))
    except ModelError as exc:
        logger.warning("摘要生成失败：%s", exc)
        raise HTTPException(status_code=502, detail="AI 服务暂时不可用，请稍后再试") from exc

    return JSONResponse({"summary": summary})
