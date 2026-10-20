"""
用户模型与 SQLite 存储。

- user_id: ``user_<16 hex>``（契约 §1）
- 邮箱归一化为小写，全局唯一
"""

import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from ..db import query_all, query_one, execute
from .security import hash_password, verify_password


class UserNotFoundError(Exception):
    """用户不存在。"""


class EmailTakenError(Exception):
    """邮箱已被占用。"""


def _new_user_id() -> str:
    return f"user_{uuid.uuid4().hex[:16]}"


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class UserStore:
    """用户 CRUD 与认证。"""

    def create_user(
        self, email: str, password: str, display_name: str = ""
    ) -> Dict[str, Any]:
        email = (email or "").strip().lower()
        if not email:
            raise ValueError("email is required")
        if not password:
            raise ValueError("password is required")

        existing = self.find_by_email(email)
        if existing:
            raise EmailTakenError("email already registered")

        user_id = _new_user_id()
        now = _now()
        password_hash = hash_password(password)
        execute(
            "INSERT INTO users (user_id, email, display_name, password_hash, created_at) "
            "VALUES (?, ?, ?, ?, ?)",
            (user_id, email, display_name or "", password_hash, now),
        )
        return self._to_public(user_id, email, display_name or "", now)

    def find_by_email(self, email: str) -> Optional[Dict[str, Any]]:
        row = query_one(
            "SELECT user_id, email, display_name, created_at FROM users WHERE email = ?",
            ((email or "").strip().lower(),),
        )
        if not row:
            return None
        return self._to_public(
            row["user_id"], row["email"], row["display_name"], row["created_at"]
        )

    def get(self, user_id: str) -> Optional[Dict[str, Any]]:
        row = query_one(
            "SELECT user_id, email, display_name, created_at FROM users WHERE user_id = ?",
            (user_id,),
        )
        if not row:
            return None
        return self._to_public(
            row["user_id"], row["email"], row["display_name"], row["created_at"]
        )

    def authenticate(self, email: str, password: str) -> Optional[Dict[str, Any]]:
        email = (email or "").strip().lower()
        row = query_one(
            "SELECT user_id, email, display_name, password_hash, created_at "
            "FROM users WHERE email = ?",
            (email,),
        )
        if not row:
            return None
        if not verify_password(password, row["password_hash"]):
            return None
        return self._to_public(
            row["user_id"], row["email"], row["display_name"], row["created_at"]
        )

    def list_users(self) -> list:
        rows = query_all(
            "SELECT user_id, email, display_name, created_at FROM users ORDER BY created_at"
        )
        return [
            self._to_public(r["user_id"], r["email"], r["display_name"], r["created_at"])
            for r in rows
        ]

    @staticmethod
    def _to_public(user_id: str, email: str, display_name: str, created_at: str) -> Dict[str, Any]:
        return {
            "user_id": user_id,
            "email": email,
            "display_name": display_name,
            "created_at": created_at,
        }


# 模块级单例，供蓝图与服务共享。
_user_store: Optional[UserStore] = None


def get_user_store() -> UserStore:
    global _user_store
    if _user_store is None:
        _user_store = UserStore()
    return _user_store
