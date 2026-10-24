"""只读查询项目资料。

AI 服务**只**读 `v_projects_public` 这个视图（它只含 ACTIVE 的公开字段），
不直接碰 projects 表——视图就是权限边界：以后加字段时，视图不暴露就等于没暴露。
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
