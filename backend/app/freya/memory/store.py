"""
按用户隔离的记忆/偏好存储（SQLite，独立于 Zep）。

- 偏好键值存储于 ``preferences`` 表，``project_id`` 区分全局偏好与项目上下文。
- 拒绝凭据/敏感键（密码、token、密钥等），绝不存储。
"""

import json
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from ..db import execute, query_all, query_one

GLOBAL_SCOPE = "global"

# 敏感键的判定子串（大小写不敏感）。
_SENSITIVE_KEY_PATTERNS = (
    "password",
    "passwd",
    "secret",
    "token",
    "api_key",
    "apikey",
    "credential",
    "authorization",
    "cookie",
    "private_key",
    "access_key",
    "client_secret",
)


class SensitiveKeyError(Exception):
    """尝试存储敏感/凭据键。"""


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


def is_sensitive_key(key: str) -> bool:
    lowered = (key or "").lower()
    return any(pattern in lowered for pattern in _SENSITIVE_KEY_PATTERNS)


class MemoryStore:
    """偏好 + 项目上下文的按用户存取。"""

    def _assert_allowed(self, key: str) -> None:
        if is_sensitive_key(key):
            raise SensitiveKeyError("credentials and sensitive values cannot be stored")

    def set_preference(
        self,
        user_id: str,
        key: str,
        value: Any,
        project_id: str = GLOBAL_SCOPE,
    ) -> None:
        self._assert_allowed(key)
        value_json = json.dumps(value, ensure_ascii=False)
        now = _now()
        execute(
            "INSERT INTO preferences (user_id, project_id, key, value, created_at, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?) "
            "ON CONFLICT(user_id, project_id, key) DO UPDATE SET value = excluded.value, "
            "updated_at = excluded.updated_at",
            (user_id, project_id or GLOBAL_SCOPE, key, value_json, now, now),
        )

    def get_preference(
        self,
        user_id: str,
        key: str,
        project_id: str = GLOBAL_SCOPE,
    ) -> Optional[Any]:
        row = query_one(
            "SELECT value FROM preferences WHERE user_id = ? AND project_id = ? AND key = ?",
            (user_id, project_id or GLOBAL_SCOPE, key),
        )
        if not row:
            return None
        return json.loads(row["value"])

    def get_preferences(self, user_id: str) -> Dict[str, Any]:
        rows = query_all(
            "SELECT key, value FROM preferences WHERE user_id = ? AND project_id = ?",
            (user_id, GLOBAL_SCOPE),
        )
        return {r["key"]: json.loads(r["value"]) for r in rows}

    def get_project_context(self, user_id: str) -> Dict[str, Dict[str, Any]]:
        rows = query_all(
            "SELECT project_id, key, value FROM preferences "
            "WHERE user_id = ? AND project_id != ?",
            (user_id, GLOBAL_SCOPE),
        )
        context: Dict[str, Dict[str, Any]] = {}
        for r in rows:
            context.setdefault(r["project_id"], {})[r["key"]] = json.loads(r["value"])
        return context

    def delete_preference(
        self,
        user_id: str,
        key: str,
        project_id: str = GLOBAL_SCOPE,
    ) -> bool:
        row = query_one(
            "SELECT 1 FROM preferences WHERE user_id = ? AND project_id = ? AND key = ?",
            (user_id, project_id or GLOBAL_SCOPE, key),
        )
        if not row:
            return False
        execute(
            "DELETE FROM preferences WHERE user_id = ? AND project_id = ? AND key = ?",
            (user_id, project_id or GLOBAL_SCOPE, key),
        )
        return True

    def search(self, user_id: str, query: str) -> List[Dict[str, Any]]:
        """在偏好与项目上下文中做不区分大小写的子串搜索。"""
        needle = (query or "").lower()
        rows = query_all(
            "SELECT project_id, key, value FROM preferences WHERE user_id = ?",
            (user_id,),
        )
        matches: List[Dict[str, Any]] = []
        for r in rows:
            key = r["key"]
            value = json.loads(r["value"])
            haystack = f"{key} {value}".lower()
            if needle in haystack:
                matches.append(
                    {
                        "type": "preference" if r["project_id"] == GLOBAL_SCOPE else "project_context",
                        "key": key,
                        "value": value,
                        "project_id": r["project_id"],
                    }
                )
        return matches
