"""调用者身份：匿名访客 vs 登录成员。

**边界说明**：AI 服务不做用户认证（方案 §1.2）——它不查库、不管会话、不签发令牌。
这里只做一件事：如果请求带了 `Authorization: Bearer <accessToken>`，就用**共享密钥**
验证签名并读出 `sub` / `role`。

验签用的是 Spring 同一个 JWT_SECRET，所以：
- 伪造的 token 验不过 → 按匿名处理（不是报错，匿名本来就允许）
- 过期的 token 也验不过 → 按匿名处理（用户自己会发现功能变弱）
- 服务端不需要额外查库，不会引入新的失败点
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

import jwt


class Tier(str, Enum):
    ANON = "anon"
    MEMBER = "member"


@dataclass(frozen=True)
class Identity:
    tier: Tier
    # 额度用的键：登录用户按 sub（换 IP 也躲不掉），匿名按 IP
    quota_key: str
    model: str
    daily_limit: int
    max_answer_chars: int
    max_tokens: int

    @property
    def logged_in(self) -> bool:
        return self.tier is Tier.MEMBER


def resolve_identity(authorization: str | None, client_ip: str, settings) -> Identity:
    subject = _subject_or_none(authorization, settings.jwt_secret)

    if subject is None:
        return Identity(
            tier=Tier.ANON,
            quota_key=f"ip:{client_ip}",
            model=settings.anon_model,
            daily_limit=settings.anon_daily_limit,
            max_answer_chars=settings.anon_max_answer_chars,
            max_tokens=settings.anon_max_tokens,
        )

    return Identity(
        tier=Tier.MEMBER,
        quota_key=f"user:{subject}",
        model=settings.member_model,
        daily_limit=settings.member_daily_limit,
        max_answer_chars=settings.member_max_answer_chars,
        max_tokens=settings.member_max_tokens,
    )


def _subject_or_none(authorization: str | None, secret: str) -> str | None:
    """验签失败一律返回 None（当匿名），不抛异常、不记错误。

    为什么不报 401：这个接口对匿名开放，"token 无效"和"没带 token"在业务上等价。
    报 401 反而会让前端多一条错误分支要处理。
    """
    if not authorization or not authorization.lower().startswith("bearer "):
        return None

    token = authorization[7:].strip()
    try:
        claims = jwt.decode(token, secret, algorithms=["HS256"], options={"verify_aud": False})
    except jwt.PyJWTError:
        return None

    subject = claims.get("sub")
    return subject if isinstance(subject, str) and subject else None
