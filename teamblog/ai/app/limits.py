"""限流与成本熔断（内存实现）。

为什么先用内存：单实例部署，重启后计数清零的代价只是"多放几个人问"。
引入 Redis 会多一个必须运维的组件——等真的要多实例时再换。

两层限制：
1. 每人每天的问题数（按 IP + 日期）
2. 单次问题长度（防止有人把整篇论文塞进 prompt 烧 token）
"""

from __future__ import annotations

import threading
from datetime import date

MAX_TRACKED_KEYS = 50_000


class DailyQuota:
    def __init__(self, limit: int) -> None:
        self._limit = limit
        self._counts: dict[str, int] = {}
        self._lock = threading.Lock()

    def try_consume(self, key: str) -> bool:
        """消耗一次额度。返回 False 表示已超限。

        键里带日期：跨天自动换桶，不需要定时清理。
        """
        today = date.today().isoformat()
        bucket = f"{today}:{key}"
        with self._lock:
            if len(self._counts) > MAX_TRACKED_KEYS:
                # 键来自外部输入（IP），无上限就会被撑爆
                self._counts.clear()
            used = self._counts.get(bucket, 0)
            if used >= self._limit:
                return False
            self._counts[bucket] = used + 1
            return True

    def remaining(self, key: str) -> int:
        today = date.today().isoformat()
        with self._lock:
            return max(0, self._limit - self._counts.get(f"{today}:{key}", 0))
