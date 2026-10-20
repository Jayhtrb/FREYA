"""
每用户速率限制（固定窗口，线程安全）。

按 user_id 记录一分钟窗口内的请求数；超过
``FREYA_RATE_LIMIT_PER_MINUTE`` 即拒绝。进程内实现，无外部依赖。
"""

import threading
import time
from typing import Dict, Optional, Tuple

from ...config import Config


class RateLimiter:
    """固定窗口速率限制器。"""

    def __init__(self, limit_per_minute: Optional[int] = None):
        self.limit = (
            limit_per_minute
            if limit_per_minute is not None
            else getattr(Config, "FREYA_RATE_LIMIT_PER_MINUTE", 20)
        )
        self._windows: Dict[str, Tuple[int, int]] = {}  # user_id -> (window_start, count)
        self._lock = threading.Lock()

    def _prune(self, now: int) -> None:
        window_start = now - 60
        stale = [k for k, (start, _) in self._windows.items() if start < window_start]
        for k in stale:
            del self._windows[k]

    def allow(self, user_id: str) -> bool:
        """尝试消费一次配额，允许返回 True。"""
        now = int(time.time())
        with self._lock:
            self._prune(now)
            start, count = self._windows.get(user_id, (now, 0))
            if start < now - 60:
                start, count = now, 0
            if count >= self.limit:
                return False
            self._windows[user_id] = (start, count + 1)
            return True

    def check(self, user_id: str) -> Tuple[bool, int, int]:
        """不消费配额，返回 (allowed, remaining, resets_in_seconds)。"""
        now = int(time.time())
        with self._lock:
            self._prune(now)
            start, count = self._windows.get(user_id, (now, 0))
            if start < now - 60:
                start, count = now, 0
            remaining = max(0, self.limit - count)
            resets_in = max(0, start + 60 - now)
            return (count < self.limit, remaining, resets_in)


_limiter: Optional[RateLimiter] = None


def get_rate_limiter() -> RateLimiter:
    global _limiter
    if _limiter is None:
        _limiter = RateLimiter()
    return _limiter
