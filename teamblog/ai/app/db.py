"""只读查询项目与笔记资料。

项目走 `v_projects_public` 视图（只含 ACTIVE 的公开字段），不直接碰 projects 表——
视图就是权限边界：以后加字段时，视图不暴露就等于没暴露。

学习笔记没有下架状态，直接读 learning_logs 的公开字段（标题、正文、日期）。
"""

from __future__ import annotations

import asyncpg

from .config import Settings


class ProjectRepository:
    def __init__(self, settings: Settings) -> None:
        self._dsn = settings.database_url
        self._pool: asyncpg.Pool | None = None

    async def connect(self) -> None:
        self._pool = await asyncpg.create_pool(self._dsn, min_size=1, max_size=5)

    async def close(self) -> None:
        if self._pool is not None:
            await self._pool.close()
            self._pool = None

    async def get_public_project(self, project_id: str) -> dict | None:
        if self._pool is None:
            return None
        row = await self._pool.fetchrow(
            """
            select id, type, title, description, content_md, github_url
              from v_projects_public
             where id = $1::uuid
            """,
            project_id,
        )
        if row is None:
            return None
        return {
            "id": str(row["id"]),
            "type": row["type"],
            "title": row["title"],
            "description": row["description"],
            "content_md": row["content_md"] or "",
            "github_url": row["github_url"],
        }

    async def get_learning_note(self, note_id: str) -> dict | None:
        """取一篇学习笔记。只取问答需要的字段，不碰用户隐私。"""
        if self._pool is None:
            return None
        row = await self._pool.fetchrow(
            """
            select l.id, l.title, l.content_md, l.log_date, u.username
              from learning_logs l
              join users u on u.id = l.user_id
             where l.id = $1::uuid
            """,
            note_id,
        )
        if row is None:
            return None
        return {
            "id": str(row["id"]),
            "title": row["title"],
            "content_md": row["content_md"] or "",
            "log_date": str(row["log_date"]),
            "author": row["username"],
        }
