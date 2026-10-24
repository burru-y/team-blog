"""限流与成本熔断（内存实现）。

为什么先用内存：单实例部署，重启后计数清零的代价只是"多放几个人问"。
引入 Redis 会多一个必须运维的组件——等真的要多实例时再换。

额度分档：匿名和成员各有一套上限（见 config.py）。
键也不同——匿名按 IP，登录按用户 id。按用户的好处是：换个 IP 也刷不动。

【约定】**上限 <= 0 表示不限**。这样配置里写 0 就是"不限"，不需要记魔法大数
（写 999999 那种值，后来人看不出是"无限"还是"真的想要九十九万"）。
"""

from __future__ import annotations

import threading
from datetime import date

MAX_TRACKED_KEYS = 50_000

# 不限额度时 remaining 的返回值：前端只展示"还能问几次"，负数表示不适用
UNLIMITED = -1


class DailyQuota:
    def __init__(self) -> None:
        self._counts: dict[str, int] = {}
        self._lock = threading.Lock()

    def try_consume(self, key: str, limit: int) -> bool:
        """消耗一次额度。返回 False 表示已超限。

        键里带日期：跨天自动换桶，不需要定时清理。
        limit <= 0 → 不限，只记数不拦。
        """
        today = date.today().isoformat()
        bucket = f"{today}:{key}"
        with self._lock:
            if len(self._counts) > MAX_TRACKED_KEYS:
                # 键来自外部输入（IP / 用户 id），无上限就会被撑爆
                self._counts.clear()
            used = self._counts.get(bucket, 0)
            if limit > 0 and used >= limit:
                return False
            # 不限额度时也要记数：日志和排查需要知道今天问了多少次
            self._counts[bucket] = used + 1
            return True

    def remaining(self, key: str, limit: int) -> int:
        if limit <= 0:
            return UNLIMITED
        today = date.today().isoformat()
        with self._lock:
            return max(0, limit - self._counts.get(f"{today}:{key}", 0))
